package top.egon.mario.im.service;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.im.facade.dto.command.ApproveCommand;
import top.egon.mario.im.facade.dto.command.CancelJoinCommand;
import top.egon.mario.im.facade.dto.command.JoinCommand;
import top.egon.mario.im.facade.dto.command.LeaveCommand;
import top.egon.mario.im.facade.dto.command.RejectJoinCommand;
import top.egon.mario.im.facade.dto.view.JoinResultView;
import top.egon.mario.im.po.ImChannelPo;
import top.egon.mario.im.po.ImConversationMemberPo;
import top.egon.mario.im.po.ImGroupPo;
import top.egon.mario.im.po.ImJoinRequestPo;
import top.egon.mario.im.po.ImMembershipPo;
import top.egon.mario.im.po.enums.ImDeliveryMode;
import top.egon.mario.im.po.enums.ImJoinPolicy;
import top.egon.mario.im.po.enums.ImJoinRequestStatus;
import top.egon.mario.im.po.enums.ImMembershipRole;
import top.egon.mario.im.po.enums.ImMembershipStatus;
import top.egon.mario.im.po.enums.ImSurfaceStatus;
import top.egon.mario.im.po.enums.ImSurfaceType;
import top.egon.mario.im.policy.ImPrincipal;
import top.egon.mario.im.repository.ImChannelRepository;
import top.egon.mario.im.repository.ImConversationMemberRepository;
import top.egon.mario.im.repository.ImGroupRepository;
import top.egon.mario.im.repository.ImJoinRequestRepository;
import top.egon.mario.im.repository.ImMembershipRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MembershipService {

    private final ImMembershipRepository membershipRepository;
    private final ImConversationMemberRepository conversationMemberRepository;
    private final ImJoinRequestRepository joinRequestRepository;
    private final ImChannelRepository channelRepository;
    private final ImGroupRepository groupRepository;
    private final EntityManager entityManager;

    public MembershipService(ImMembershipRepository membershipRepository,
                             ImConversationMemberRepository conversationMemberRepository,
                             ImJoinRequestRepository joinRequestRepository,
                             ImChannelRepository channelRepository,
                             ImGroupRepository groupRepository,
                             EntityManager entityManager) {
        this.membershipRepository = membershipRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.joinRequestRepository = joinRequestRepository;
        this.channelRepository = channelRepository;
        this.groupRepository = groupRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public ImMembershipPo ensureOwnerMembership(ImSurfaceType surfaceType, Long surfaceId, Long userId, Instant now) {
        return membershipRepository.findBySurfaceTypeAndSurfaceIdAndUserIdAndDeletedFalse(
                        surfaceType, surfaceId, userId)
                .map(membership -> activateOwner(membership, now))
                .orElseGet(() -> membershipRepository.saveAndFlush(ownerMembership(surfaceType, surfaceId, userId, now)));
    }

    @Transactional
    public ImConversationMemberPo ensureConversationMember(Long conversationId, Long userId, Instant now) {
        Optional<ImConversationMemberPo> existing = conversationMemberRepository
                .findByConversationIdAndUserIdAndDeletedFalse(conversationId, userId);
        if (existing.isPresent()) {
            return activateConversationMember(existing.get(), now);
        }
        return conversationMemberRepository.saveAndFlush(conversationMember(conversationId, userId, now));
    }

    @Transactional(readOnly = true)
    public Optional<ImMembershipPo> findCallerMembership(ImSurfaceType surfaceType, Long surfaceId, Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return membershipRepository.findBySurfaceTypeAndSurfaceIdAndUserIdAndDeletedFalse(
                surfaceType, surfaceId, userId);
    }

    @Transactional(readOnly = true)
    public Map<Long, ImMembershipPo> findCallerMemberships(ImSurfaceType surfaceType,
                                                           Collection<Long> surfaceIds,
                                                           Long userId) {
        if (userId == null || surfaceIds == null || surfaceIds.isEmpty()) {
            return Map.of();
        }
        return membershipRepository.findBySurfaceTypeAndSurfaceIdInAndUserIdAndDeletedFalse(
                        surfaceType, surfaceIds, userId)
                .stream()
                .collect(Collectors.toMap(ImMembershipPo::getSurfaceId, Function.identity(), (first, second) -> first));
    }

    @Transactional
    public JoinResultView applyJoin(JoinCommand command) {
        if (command == null) {
            throw new ImException("IM_JOIN_COMMAND_REQUIRED");
        }
        ImPrincipal principal = requirePrincipal(command.principal());
        ImSurfaceType surfaceType = surfaceType(command.surfaceType());
        Long surfaceId = requireId(command.surfaceId(), "IM_SURFACE_ID_REQUIRED");
        SurfaceRef surface = requireLockedSurface(surfaceType, surfaceId);

        Optional<ImMembershipPo> existingMembership = membershipRepository
                .findBySurfaceTypeAndSurfaceIdAndUserIdAndDeletedFalse(surfaceType, surfaceId, principal.userId());
        if (existingMembership.filter(membership -> ImMembershipStatus.ACTIVE.equals(membership.getStatus())).isPresent()) {
            return activeResult(surface, existingMembership.get(), null);
        }
        if (existingMembership.filter(membership -> ImMembershipStatus.BANNED.equals(membership.getStatus())).isPresent()) {
            throw new ImException("IM_MEMBER_BANNED");
        }

        if (ImJoinPolicy.OPEN.equals(surface.joinPolicy())) {
            ImMembershipPo membership = activateMember(surface, principal.userId(), Instant.now());
            return activeResult(surface, membership, null);
        }

        ImJoinRequestPo request = findPendingJoinRequest(surfaceType, surfaceId, principal.userId())
                .orElseGet(() -> joinRequestRepository.saveAndFlush(joinRequest(surface, principal.userId())));
        return new JoinResultView(ImJoinRequestStatus.PENDING.name(), surfaceType.name(), surfaceId, null,
                request.getId());
    }

    @Transactional
    public JoinResultView approveJoin(ApproveCommand command) {
        if (command == null) {
            throw new ImException("IM_APPROVE_COMMAND_REQUIRED");
        }
        ImPrincipal principal = requirePrincipal(command.principal());
        ImJoinRequestPo request = requireRequest(command.joinRequestId());
        SurfaceRef surface = requireLockedSurface(request.getSurfaceType(), request.getSurfaceId());
        refreshRequest(request);
        requirePending(request);
        requireDecisionActor(surface, principal.userId());

        request.setStatus(ImJoinRequestStatus.APPROVED);
        request.setDecidedBy(principal.userId());
        request.setDecidedAt(Instant.now());
        joinRequestRepository.saveAndFlush(request);

        ImMembershipPo membership = activateMember(surface, request.getUserId(), Instant.now());
        return activeResult(surface, membership, request.getId());
    }

    @Transactional
    public JoinResultView rejectJoin(RejectJoinCommand command) {
        if (command == null) {
            throw new ImException("IM_REJECT_COMMAND_REQUIRED");
        }
        ImPrincipal principal = requirePrincipal(command.principal());
        ImJoinRequestPo request = requireRequest(command.joinRequestId());
        SurfaceRef surface = requireLockedSurface(request.getSurfaceType(), request.getSurfaceId());
        refreshRequest(request);
        requirePending(request);
        requireDecisionActor(surface, principal.userId());

        request.setStatus(ImJoinRequestStatus.REJECTED);
        request.setDecidedBy(principal.userId());
        request.setDecidedAt(Instant.now());
        request.setDecisionReason(decisionReason(command.reason()));
        joinRequestRepository.saveAndFlush(request);
        deactivateRequesterForDecision(surface, request.getUserId());
        return new JoinResultView(ImJoinRequestStatus.REJECTED.name(), surface.surfaceType().name(),
                surface.surfaceId(), null, request.getId());
    }

    @Transactional
    public JoinResultView cancelJoin(CancelJoinCommand command) {
        if (command == null) {
            throw new ImException("IM_CANCEL_JOIN_COMMAND_REQUIRED");
        }
        ImPrincipal principal = requirePrincipal(command.principal());
        ImJoinRequestPo request = requireRequest(command.joinRequestId());
        SurfaceRef surface = requireLockedSurface(request.getSurfaceType(), request.getSurfaceId());
        refreshRequest(request);
        requirePending(request);
        if (!principal.userId().equals(request.getUserId())) {
            throw new ImException("IM_JOIN_REQUEST_OWNER_REQUIRED");
        }

        request.setStatus(ImJoinRequestStatus.CANCELLED);
        request.setDecidedBy(principal.userId());
        request.setDecidedAt(Instant.now());
        joinRequestRepository.saveAndFlush(request);
        deactivateRequesterForDecision(surface, request.getUserId());
        return new JoinResultView(ImJoinRequestStatus.CANCELLED.name(), surface.surfaceType().name(),
                surface.surfaceId(), null, request.getId());
    }

    @Transactional
    public void leave(LeaveCommand command) {
        if (command == null) {
            throw new ImException("IM_LEAVE_COMMAND_REQUIRED");
        }
        ImPrincipal principal = requirePrincipal(command.principal());
        ImSurfaceType surfaceType = surfaceType(command.surfaceType());
        Long surfaceId = requireId(command.surfaceId(), "IM_SURFACE_ID_REQUIRED");
        SurfaceRef surface = requireLockedSurface(surfaceType, surfaceId);
        ImMembershipPo membership = membershipRepository
                .findBySurfaceTypeAndSurfaceIdAndUserIdAndStatusAndDeletedFalse(
                        surfaceType, surfaceId, principal.userId(), ImMembershipStatus.ACTIVE)
                .orElseThrow(() -> new ImException("IM_MEMBER_NOT_ACTIVE"));
        if (ImMembershipRole.OWNER.equals(membership.getMemberRole()) && !hasReplacementOwner(surface, principal.userId())) {
            throw new ImException("IM_OWNER_REPLACEMENT_REQUIRED");
        }

        membership.setStatus(ImMembershipStatus.LEFT);
        membershipRepository.saveAndFlush(membership);
        conversationMemberRepository.findByConversationIdAndUserIdAndDeletedFalse(
                        surface.conversationId(), principal.userId())
                .ifPresent(member -> {
                    member.setStatus(ImMembershipStatus.LEFT);
                    conversationMemberRepository.saveAndFlush(member);
                });
        decrementMemberCount(surface);
    }

    private ImMembershipPo activateOwner(ImMembershipPo membership, Instant now) {
        membership.setMemberRole(ImMembershipRole.OWNER);
        membership.setStatus(ImMembershipStatus.ACTIVE);
        if (membership.getJoinedAt() == null) {
            membership.setJoinedAt(now);
        }
        return membership;
    }

    private ImConversationMemberPo activateConversationMember(ImConversationMemberPo member, Instant now) {
        member.setStatus(ImMembershipStatus.ACTIVE);
        member.setDeliveryMode(ImDeliveryMode.INBOX);
        member.setMuted(false);
        if (member.getLastReadSeq() == null) {
            member.setLastReadSeq(0L);
        }
        return member;
    }

    private ImMembershipPo ownerMembership(ImSurfaceType surfaceType, Long surfaceId, Long userId, Instant now) {
        ImMembershipPo membership = new ImMembershipPo();
        membership.setSurfaceType(surfaceType);
        membership.setSurfaceId(surfaceId);
        membership.setUserId(userId);
        membership.setMemberRole(ImMembershipRole.OWNER);
        membership.setStatus(ImMembershipStatus.ACTIVE);
        membership.setJoinedAt(now);
        membership.setMetadataJson("{}");
        return membership;
    }

    private ImConversationMemberPo conversationMember(Long conversationId, Long userId, Instant now) {
        ImConversationMemberPo member = new ImConversationMemberPo();
        member.setConversationId(conversationId);
        member.setUserId(userId);
        member.setLastReadSeq(0L);
        member.setDeliveryMode(ImDeliveryMode.INBOX);
        member.setMuted(false);
        member.setStatus(ImMembershipStatus.ACTIVE);
        member.setJoinedAt(now);
        member.setLastActiveAt(now);
        member.setMetadataJson("{}");
        return member;
    }

    private ImMembershipPo activateMember(SurfaceRef surface, Long userId, Instant now) {
        Optional<ImMembershipPo> existing = membershipRepository.findBySurfaceTypeAndSurfaceIdAndUserIdAndDeletedFalse(
                surface.surfaceType(), surface.surfaceId(), userId);
        existing.filter(membership -> ImMembershipStatus.BANNED.equals(membership.getStatus()))
                .ifPresent(membership -> {
                    throw new ImException("IM_MEMBER_BANNED");
                });
        boolean transitionToActive = existing
                .map(membership -> !ImMembershipStatus.ACTIVE.equals(membership.getStatus()))
                .orElse(true);
        ImMembershipPo membership = existing.orElseGet(() -> memberMembership(
                surface.surfaceType(), surface.surfaceId(), userId, now));
        membership.setMemberRole(ImMembershipRole.MEMBER);
        membership.setStatus(ImMembershipStatus.ACTIVE);
        if (membership.getJoinedAt() == null) {
            membership.setJoinedAt(now);
        }
        membership = membershipRepository.saveAndFlush(membership);
        ensureConversationMember(surface.conversationId(), userId, now);
        if (transitionToActive) {
            incrementMemberCount(surface);
        }
        return membership;
    }

    private Optional<ImJoinRequestPo> findPendingJoinRequest(ImSurfaceType surfaceType, Long surfaceId, Long userId) {
        return joinRequestRepository.findBySurfaceTypeAndSurfaceIdAndUserIdAndStatusAndDeletedFalse(
                surfaceType, surfaceId, userId, ImJoinRequestStatus.PENDING);
    }

    private void deactivateRequesterForDecision(SurfaceRef surface, Long userId) {
        membershipRepository.findBySurfaceTypeAndSurfaceIdAndUserIdAndDeletedFalse(
                        surface.surfaceType(), surface.surfaceId(), userId)
                .filter(membership -> !ImMembershipStatus.BANNED.equals(membership.getStatus()))
                .ifPresent(membership -> {
                    boolean wasActive = ImMembershipStatus.ACTIVE.equals(membership.getStatus());
                    membership.setStatus(ImMembershipStatus.LEFT);
                    membershipRepository.saveAndFlush(membership);
                    if (wasActive) {
                        decrementMemberCount(surface);
                    }
                });
        conversationMemberRepository.findByConversationIdAndUserIdAndDeletedFalse(surface.conversationId(), userId)
                .filter(member -> ImMembershipStatus.ACTIVE.equals(member.getStatus()))
                .ifPresent(member -> {
                    member.setStatus(ImMembershipStatus.LEFT);
                    conversationMemberRepository.saveAndFlush(member);
                });
    }

    private ImJoinRequestPo joinRequest(SurfaceRef surface, Long userId) {
        ImJoinRequestPo request = new ImJoinRequestPo();
        request.setSurfaceType(surface.surfaceType());
        request.setSurfaceId(surface.surfaceId());
        request.setUserId(userId);
        request.setStatus(ImJoinRequestStatus.PENDING);
        request.setMetadataJson("{}");
        return request;
    }

    private ImMembershipPo memberMembership(ImSurfaceType surfaceType, Long surfaceId, Long userId, Instant now) {
        ImMembershipPo membership = new ImMembershipPo();
        membership.setSurfaceType(surfaceType);
        membership.setSurfaceId(surfaceId);
        membership.setUserId(userId);
        membership.setMemberRole(ImMembershipRole.MEMBER);
        membership.setStatus(ImMembershipStatus.ACTIVE);
        membership.setJoinedAt(now);
        membership.setMetadataJson("{}");
        return membership;
    }

    private ImJoinRequestPo requireRequest(Long joinRequestId) {
        Long id = requireId(joinRequestId, "IM_JOIN_REQUEST_ID_REQUIRED");
        return joinRequestRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ImException("IM_JOIN_REQUEST_NOT_FOUND"));
    }

    private void requirePending(ImJoinRequestPo request) {
        if (!ImJoinRequestStatus.PENDING.equals(request.getStatus())) {
            throw new ImException("IM_JOIN_REQUEST_NOT_PENDING");
        }
    }

    private void refreshRequest(ImJoinRequestPo request) {
        entityManager.refresh(request);
    }

    private void requireDecisionActor(SurfaceRef surface, Long userId) {
        membershipRepository.findBySurfaceTypeAndSurfaceIdAndUserIdAndStatusAndDeletedFalse(
                        surface.surfaceType(), surface.surfaceId(), userId, ImMembershipStatus.ACTIVE)
                .filter(membership -> ImMembershipRole.OWNER.equals(membership.getMemberRole())
                        || ImMembershipRole.ADMIN.equals(membership.getMemberRole()))
                .orElseThrow(() -> new ImException("IM_JOIN_APPROVER_REQUIRED"));
    }

    private boolean hasReplacementOwner(SurfaceRef surface, Long leavingUserId) {
        return membershipRepository.findBySurfaceTypeAndSurfaceIdAndStatusAndDeletedFalse(
                        surface.surfaceType(), surface.surfaceId(), ImMembershipStatus.ACTIVE)
                .stream()
                .anyMatch(membership -> !leavingUserId.equals(membership.getUserId())
                        && ImMembershipRole.OWNER.equals(membership.getMemberRole()));
    }

    private SurfaceRef requireLockedSurface(ImSurfaceType surfaceType, Long surfaceId) {
        if (ImSurfaceType.CHANNEL.equals(surfaceType)) {
            ImChannelPo channel = channelRepository.findLockedByIdAndDeletedFalse(surfaceId)
                    .filter(candidate -> ImSurfaceStatus.ACTIVE.equals(candidate.getStatus()))
                    .orElseThrow(() -> new ImException("IM_CHANNEL_NOT_FOUND"));
            if (channel.getMainConversationId() == null) {
                throw new ImException("IM_SURFACE_CONVERSATION_REQUIRED");
            }
            return new SurfaceRef(surfaceType, channel.getId(), channel.getJoinPolicy(),
                    channel.getMainConversationId(), channel, null);
        }
        if (ImSurfaceType.GROUP.equals(surfaceType)) {
            ImGroupPo group = groupRepository.findLockedByIdAndDeletedFalse(surfaceId)
                    .filter(candidate -> ImSurfaceStatus.ACTIVE.equals(candidate.getStatus()))
                    .orElseThrow(() -> new ImException("IM_GROUP_NOT_FOUND"));
            if (group.getConversationId() == null) {
                throw new ImException("IM_SURFACE_CONVERSATION_REQUIRED");
            }
            return new SurfaceRef(surfaceType, group.getId(), group.getJoinPolicy(),
                    group.getConversationId(), null, group);
        }
        throw new ImException("IM_SURFACE_TYPE_UNSUPPORTED");
    }

    private void incrementMemberCount(SurfaceRef surface) {
        if (surface.channel() != null) {
            surface.channel().setMemberCount(memberCount(surface.channel().getMemberCount()) + 1);
            channelRepository.saveAndFlush(surface.channel());
            return;
        }
        surface.group().setMemberCount(memberCount(surface.group().getMemberCount()) + 1);
        groupRepository.saveAndFlush(surface.group());
    }

    private void decrementMemberCount(SurfaceRef surface) {
        if (surface.channel() != null) {
            surface.channel().setMemberCount(Math.max(0, memberCount(surface.channel().getMemberCount()) - 1));
            channelRepository.saveAndFlush(surface.channel());
            return;
        }
        surface.group().setMemberCount(Math.max(0, memberCount(surface.group().getMemberCount()) - 1));
        groupRepository.saveAndFlush(surface.group());
    }

    private JoinResultView activeResult(SurfaceRef surface, ImMembershipPo membership, Long joinRequestId) {
        return new JoinResultView(ImMembershipStatus.ACTIVE.name(), surface.surfaceType().name(),
                surface.surfaceId(), membership.getId(), joinRequestId);
    }

    private ImPrincipal requirePrincipal(ImPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new ImException("IM_PRINCIPAL_REQUIRED");
        }
        return principal;
    }

    private ImSurfaceType surfaceType(String value) {
        String surfaceType = requireText(value, "IM_SURFACE_TYPE_REQUIRED");
        try {
            ImSurfaceType type = ImSurfaceType.valueOf(surfaceType.toUpperCase(Locale.ROOT));
            if (ImSurfaceType.DM_PAIR.equals(type)) {
                throw new ImException("IM_SURFACE_TYPE_UNSUPPORTED");
            }
            return type;
        } catch (IllegalArgumentException ex) {
            throw new ImException("IM_SURFACE_TYPE_INVALID", surfaceType);
        }
    }

    private Long requireId(Long id, String code) {
        if (id == null) {
            throw new ImException(code);
        }
        return id;
    }

    private String requireText(String value, String code) {
        if (!StringUtils.hasText(value)) {
            throw new ImException(code);
        }
        return value.trim();
    }

    private String decisionReason(String reason) {
        return StringUtils.hasText(reason) ? reason.trim() : null;
    }

    private int memberCount(Integer value) {
        return value == null ? 0 : value;
    }

    private record SurfaceRef(ImSurfaceType surfaceType, Long surfaceId, ImJoinPolicy joinPolicy, Long conversationId,
                              ImChannelPo channel, ImGroupPo group) {
    }
}
