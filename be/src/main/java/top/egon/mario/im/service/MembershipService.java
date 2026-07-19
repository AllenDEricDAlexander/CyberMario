package top.egon.mario.im.service;

import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.im.service.ImException;
import top.egon.mario.im.facade.dto.command.ApproveCommand;
import top.egon.mario.im.facade.dto.command.CancelJoinCommand;
import top.egon.mario.im.facade.dto.command.JoinCommand;
import top.egon.mario.im.facade.dto.command.JoinByKeyCommand;
import top.egon.mario.im.facade.dto.command.LeaveCommand;
import top.egon.mario.im.facade.dto.command.RejectJoinCommand;
import top.egon.mario.im.facade.dto.command.RemoveMemberCommand;
import top.egon.mario.im.facade.dto.query.ListJoinRequestsQuery;
import top.egon.mario.im.facade.dto.query.ListSurfaceMembersQuery;
import top.egon.mario.im.facade.dto.view.JoinRequestView;
import top.egon.mario.im.facade.dto.view.JoinResultView;
import top.egon.mario.im.facade.dto.view.SurfaceMemberView;
import top.egon.mario.im.po.ImChannelPo;
import top.egon.mario.im.po.ImConversationMemberPo;
import top.egon.mario.im.po.ImGroupPo;
import top.egon.mario.im.po.ImJoinRequestPo;
import top.egon.mario.im.po.ImMembershipPo;
import top.egon.mario.im.po.ImSurfaceInvitationPo;
import top.egon.mario.im.po.enums.ImDeliveryMode;
import top.egon.mario.im.po.enums.ImJoinPolicy;
import top.egon.mario.im.po.enums.ImJoinRequestStatus;
import top.egon.mario.im.po.enums.ImMembershipRole;
import top.egon.mario.im.po.enums.ImMembershipStatus;
import top.egon.mario.im.po.enums.ImSurfaceStatus;
import top.egon.mario.im.po.enums.ImSurfaceInvitationStatus;
import top.egon.mario.im.po.enums.ImSurfaceType;
import top.egon.mario.im.policy.ImPrincipal;
import top.egon.mario.im.repository.ImChannelRepository;
import top.egon.mario.im.repository.ImConversationMemberRepository;
import top.egon.mario.im.repository.ImGroupRepository;
import top.egon.mario.im.repository.ImJoinRequestRepository;
import top.egon.mario.im.repository.ImMembershipRepository;
import top.egon.mario.im.repository.ImSurfaceInvitationRepository;
import top.egon.mario.im.cache.ImSurfaceJoinKeyRef;
import top.egon.mario.rbac.application.RbacUserDirectoryFacade;
import top.egon.mario.rbac.dto.response.UserDirectoryItemResponse;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MembershipService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String PLATFORM_CONTEXT_TYPE = "PLATFORM";

    private final ImMembershipRepository membershipRepository;
    private final ImConversationMemberRepository conversationMemberRepository;
    private final ImJoinRequestRepository joinRequestRepository;
    private final ImChannelRepository channelRepository;
    private final ImGroupRepository groupRepository;
    private final ImSurfaceInvitationRepository surfaceInvitationRepository;
    private final EntityManager entityManager;
    private final RbacUserDirectoryFacade userDirectoryFacade;
    private final ImSurfaceJoinKeyService surfaceJoinKeyService;

    public MembershipService(ImMembershipRepository membershipRepository,
                             ImConversationMemberRepository conversationMemberRepository,
                             ImJoinRequestRepository joinRequestRepository,
                             ImChannelRepository channelRepository,
                             ImGroupRepository groupRepository,
                             ImSurfaceInvitationRepository surfaceInvitationRepository,
                             EntityManager entityManager,
                             RbacUserDirectoryFacade userDirectoryFacade,
                             ImSurfaceJoinKeyService surfaceJoinKeyService) {
        this.membershipRepository = membershipRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.joinRequestRepository = joinRequestRepository;
        this.channelRepository = channelRepository;
        this.groupRepository = groupRepository;
        this.surfaceInvitationRepository = surfaceInvitationRepository;
        this.entityManager = entityManager;
        this.userDirectoryFacade = userDirectoryFacade;
        this.surfaceJoinKeyService = surfaceJoinKeyService;
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
        return applyJoin(principal, surfaceType, surfaceId, false);
    }

    @Transactional
    public JoinResultView applyJoinByKey(JoinByKeyCommand command) {
        if (command == null) {
            throw new ImException("IM_JOIN_COMMAND_REQUIRED");
        }
        ImPrincipal principal = requirePrincipal(command.principal());
        ImSurfaceJoinKeyRef surface = surfaceJoinKeyService.resolve(command.joinKey());
        return applyJoin(principal, surface.surfaceType(), surface.surfaceId(), true);
    }

    private JoinResultView applyJoin(
            ImPrincipal principal,
            ImSurfaceType surfaceType,
            Long surfaceId,
            boolean joinedByKey
    ) {
        SurfaceRef surface = requireLockedSurface(surfaceType, surfaceId);

        Optional<ImMembershipPo> existingMembership = membershipRepository
                .findBySurfaceTypeAndSurfaceIdAndUserIdAndDeletedFalse(surfaceType, surfaceId, principal.userId());
        if (existingMembership.filter(membership -> ImMembershipStatus.ACTIVE.equals(membership.getStatus())).isPresent()) {
            return activeResult(surface, existingMembership.get(), null);
        }
        if (existingMembership.filter(membership -> ImMembershipStatus.BANNED.equals(membership.getStatus())).isPresent()) {
            throw new ImException("IM_MEMBER_BANNED");
        }
        requirePlatformJoinAllowed(surface, principal.userId(), joinedByKey);

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
        requireActiveParentChannelMembership(surface, request.getUserId());

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

    @Transactional(readOnly = true)
    public Page<SurfaceMemberView> listMembers(ListSurfaceMembersQuery query) {
        if (query == null) {
            throw new ImException("IM_MEMBER_LIST_QUERY_REQUIRED");
        }
        ImPrincipal principal = requirePrincipal(query.principal());
        ImSurfaceType surfaceType = surfaceType(query.surfaceType());
        Long surfaceId = requireId(query.surfaceId(), "IM_SURFACE_ID_REQUIRED");
        requireActiveSurface(surfaceType, surfaceId);
        requireManagementActor(surfaceType, surfaceId, principal.userId());

        PageRequest pageable = pageRequest(query.page(), query.size(),
                Sort.by(Sort.Order.asc("joinedAt"), Sort.Order.asc("id")));
        Page<ImMembershipPo> memberships = membershipRepository
                .findBySurfaceTypeAndSurfaceIdAndStatusAndDeletedFalse(
                        surfaceType, surfaceId, ImMembershipStatus.ACTIVE, pageable);
        Map<Long, UserDirectoryItemResponse> users = userDirectoryFacade.findEnabledByIds(
                memberships.getContent().stream().map(ImMembershipPo::getUserId).toList());
        return new PageImpl<>(memberships.getContent().stream()
                .map(membership -> memberView(membership, users.get(membership.getUserId())))
                .toList(), pageable, memberships.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Page<JoinRequestView> listJoinRequests(ListJoinRequestsQuery query) {
        if (query == null) {
            throw new ImException("IM_JOIN_REQUEST_LIST_QUERY_REQUIRED");
        }
        ImPrincipal principal = requirePrincipal(query.principal());
        ImSurfaceType surfaceType = surfaceType(query.surfaceType());
        Long surfaceId = requireId(query.surfaceId(), "IM_SURFACE_ID_REQUIRED");
        requireActiveSurface(surfaceType, surfaceId);
        requireManagementActor(surfaceType, surfaceId, principal.userId());

        PageRequest pageable = pageRequest(query.page(), query.size(),
                Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id")));
        Page<ImJoinRequestPo> requests = joinRequestRepository
                .findBySurfaceTypeAndSurfaceIdAndStatusAndDeletedFalse(
                        surfaceType, surfaceId, ImJoinRequestStatus.PENDING, pageable);
        Map<Long, UserDirectoryItemResponse> users = userDirectoryFacade.findEnabledByIds(
                requests.getContent().stream().map(ImJoinRequestPo::getUserId).toList());
        return new PageImpl<>(requests.getContent().stream()
                .map(request -> joinRequestView(request, users.get(request.getUserId())))
                .toList(), pageable, requests.getTotalElements());
    }

    @Transactional
    public void removeMember(RemoveMemberCommand command) {
        if (command == null) {
            throw new ImException("IM_MEMBER_REMOVE_COMMAND_REQUIRED");
        }
        ImPrincipal principal = requirePrincipal(command.principal());
        ImSurfaceType surfaceType = surfaceType(command.surfaceType());
        Long surfaceId = requireId(command.surfaceId(), "IM_SURFACE_ID_REQUIRED");
        Long userId = requireId(command.userId(), "IM_USER_ID_REQUIRED");
        if (principal.userId().equals(userId)) {
            throw new ImException("IM_MEMBER_SELF_REMOVE_DENIED");
        }
        SurfaceRef surface = requireLockedSurface(surfaceType, surfaceId);
        requireManagementActor(surfaceType, surfaceId, principal.userId());
        ImMembershipPo membership = membershipRepository
                .findBySurfaceTypeAndSurfaceIdAndUserIdAndStatusAndDeletedFalse(
                        surfaceType, surfaceId, userId, ImMembershipStatus.ACTIVE)
                .orElseThrow(() -> new ImException("IM_MEMBER_NOT_ACTIVE"));
        if (!ImMembershipRole.MEMBER.equals(membership.getMemberRole())) {
            throw new ImException("IM_MEMBER_REMOVE_ROLE_DENIED");
        }
        requireNoOwnedPlatformChildGroups(surface, userId);

        deactivateMembership(surface, membership, userId);
        cascadePlatformChannelDeparture(surface, userId, principal.userId());
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
        requireNoOwnedPlatformChildGroups(surface, principal.userId());

        deactivateMembership(surface, membership, principal.userId());
        cascadePlatformChannelDeparture(surface, principal.userId(), principal.userId());
    }

    @Transactional
    public JoinResultView acceptInvitation(ImSurfaceType surfaceType, Long surfaceId, Long userId) {
        SurfaceRef surface = requireLockedSurface(surfaceType, surfaceId);
        if (!PLATFORM_CONTEXT_TYPE.equals(surface.contextType())) {
            throw new ImException("IM_PLATFORM_SURFACE_REQUIRED");
        }
        requireActiveParentChannelMembership(surface, userId);
        ImMembershipPo membership = activateMember(surface, userId, Instant.now());
        return activeResult(surface, membership, null);
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

    private void requirePlatformJoinAllowed(SurfaceRef surface, Long userId, boolean joinedByKey) {
        if (!PLATFORM_CONTEXT_TYPE.equals(surface.contextType())) {
            return;
        }
        if (ImSurfaceType.CHANNEL.equals(surface.surfaceType()) || surface.parentChannelId() == null) {
            if (!joinedByKey) {
                throw new ImException("IM_PLATFORM_JOIN_KEY_REQUIRED");
            }
            return;
        }
        requireActiveParentChannelMembership(surface, userId);
    }

    private void requireActiveParentChannelMembership(SurfaceRef surface, Long userId) {
        if (!PLATFORM_CONTEXT_TYPE.equals(surface.contextType()) || surface.parentChannelId() == null) {
            return;
        }
        membershipRepository.findBySurfaceTypeAndSurfaceIdAndUserIdAndStatusAndDeletedFalse(
                        ImSurfaceType.CHANNEL, surface.parentChannelId(), userId, ImMembershipStatus.ACTIVE)
                .orElseThrow(() -> new ImException("IM_PARENT_CHANNEL_MEMBERSHIP_REQUIRED"));
    }

    private void requireNoOwnedPlatformChildGroups(SurfaceRef surface, Long userId) {
        if (!PLATFORM_CONTEXT_TYPE.equals(surface.contextType())
                || !ImSurfaceType.CHANNEL.equals(surface.surfaceType())) {
            return;
        }
        boolean ownsChildGroup = groupRepository.findActiveByChannelId(surface.surfaceId()).stream()
                .anyMatch(group -> userId.equals(group.getOwnerUserId()));
        if (ownsChildGroup) {
            throw new ImException("IM_CHILD_GROUP_OWNER_TRANSFER_REQUIRED");
        }
    }

    private void deactivateMembership(SurfaceRef surface, ImMembershipPo membership, Long userId) {
        membership.setStatus(ImMembershipStatus.LEFT);
        membershipRepository.saveAndFlush(membership);
        conversationMemberRepository.findByConversationIdAndUserIdAndDeletedFalse(surface.conversationId(), userId)
                .filter(member -> ImMembershipStatus.ACTIVE.equals(member.getStatus()))
                .ifPresent(member -> {
                    member.setStatus(ImMembershipStatus.LEFT);
                    conversationMemberRepository.saveAndFlush(member);
                });
        decrementMemberCount(surface);
    }

    private void cascadePlatformChannelDeparture(SurfaceRef surface, Long userId, Long actorUserId) {
        if (!PLATFORM_CONTEXT_TYPE.equals(surface.contextType())
                || !ImSurfaceType.CHANNEL.equals(surface.surfaceType())) {
            return;
        }
        List<ImGroupPo> childGroups = groupRepository.findActiveByChannelId(surface.surfaceId());
        List<Long> childGroupIds = childGroups.stream().map(ImGroupPo::getId).toList();
        if (childGroupIds.isEmpty()) {
            return;
        }
        Map<Long, ImGroupPo> childGroupsById = childGroups.stream()
                .collect(Collectors.toMap(ImGroupPo::getId, Function.identity()));
        membershipRepository.findBySurfaceTypeAndSurfaceIdInAndUserIdAndDeletedFalse(
                        ImSurfaceType.GROUP, childGroupIds, userId).stream()
                .filter(childMembership -> ImMembershipStatus.ACTIVE.equals(childMembership.getStatus()))
                .forEach(childMembership -> deactivateMembership(
                        childSurface(childGroupsById.get(childMembership.getSurfaceId())), childMembership, userId));
        Instant now = Instant.now();
        joinRequestRepository.findBySurfaceTypeAndSurfaceIdInAndUserIdAndStatusAndDeletedFalse(
                        ImSurfaceType.GROUP, childGroupIds, userId, ImJoinRequestStatus.PENDING)
                .forEach(request -> {
                    request.setStatus(ImJoinRequestStatus.CANCELLED);
                    request.setDecidedBy(actorUserId);
                    request.setDecidedAt(now);
                    joinRequestRepository.save(request);
                });
        surfaceInvitationRepository.findBySurfaceTypeAndSurfaceIdInAndInviteeUserIdAndStatusAndDeletedFalse(
                        ImSurfaceType.GROUP, childGroupIds, userId, ImSurfaceInvitationStatus.PENDING)
                .forEach(invitation -> {
                    invitation.setStatus(ImSurfaceInvitationStatus.CANCELLED);
                    invitation.setRespondedAt(now);
                    surfaceInvitationRepository.save(invitation);
                });
    }

    private SurfaceRef childSurface(ImGroupPo group) {
        return new SurfaceRef(ImSurfaceType.GROUP, group.getId(), group.getJoinPolicy(),
                group.getConversationId(), group.getContextType(), group.getChannelId(), null, group);
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

    private void requireManagementActor(ImSurfaceType surfaceType, Long surfaceId, Long userId) {
        membershipRepository.findBySurfaceTypeAndSurfaceIdAndUserIdAndStatusAndDeletedFalse(
                        surfaceType, surfaceId, userId, ImMembershipStatus.ACTIVE)
                .filter(membership -> ImMembershipRole.OWNER.equals(membership.getMemberRole())
                        || ImMembershipRole.ADMIN.equals(membership.getMemberRole()))
                .orElseThrow(() -> new ImException("IM_SURFACE_MANAGEMENT_REQUIRED"));
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
                    channel.getMainConversationId(), channel.getContextType(), null, channel, null);
        }
        if (ImSurfaceType.GROUP.equals(surfaceType)) {
            ImGroupPo group = groupRepository.findLockedByIdAndDeletedFalse(surfaceId)
                    .filter(candidate -> ImSurfaceStatus.ACTIVE.equals(candidate.getStatus()))
                    .orElseThrow(() -> new ImException("IM_GROUP_NOT_FOUND"));
            if (group.getConversationId() == null) {
                throw new ImException("IM_SURFACE_CONVERSATION_REQUIRED");
            }
            return new SurfaceRef(surfaceType, group.getId(), group.getJoinPolicy(),
                    group.getConversationId(), group.getContextType(), group.getChannelId(), null, group);
        }
        throw new ImException("IM_SURFACE_TYPE_UNSUPPORTED");
    }

    private void requireActiveSurface(ImSurfaceType surfaceType, Long surfaceId) {
        if (ImSurfaceType.CHANNEL.equals(surfaceType)) {
            channelRepository.findByIdAndDeletedFalse(surfaceId)
                    .filter(candidate -> ImSurfaceStatus.ACTIVE.equals(candidate.getStatus()))
                    .orElseThrow(() -> new ImException("IM_CHANNEL_NOT_FOUND"));
            return;
        }
        if (ImSurfaceType.GROUP.equals(surfaceType)) {
            groupRepository.findByIdAndDeletedFalse(surfaceId)
                    .filter(candidate -> ImSurfaceStatus.ACTIVE.equals(candidate.getStatus()))
                    .orElseThrow(() -> new ImException("IM_GROUP_NOT_FOUND"));
            return;
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

    private PageRequest pageRequest(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), sort);
    }

    private SurfaceMemberView memberView(ImMembershipPo membership, UserDirectoryItemResponse user) {
        return new SurfaceMemberView(
                membership.getId(),
                membership.getUserId(),
                user == null ? null : user.accountNo(),
                user == null ? "用户 " + membership.getUserId() : user.displayName(),
                user == null ? null : user.avatarUrl(),
                user != null,
                membership.getMemberRole().name(),
                membership.getStatus().name(),
                membership.getMutedUntil(),
                membership.getJoinedAt()
        );
    }

    private JoinRequestView joinRequestView(ImJoinRequestPo request, UserDirectoryItemResponse user) {
        return new JoinRequestView(
                request.getId(),
                request.getUserId(),
                user == null ? null : user.accountNo(),
                user == null ? "用户 " + request.getUserId() : user.displayName(),
                user == null ? null : user.avatarUrl(),
                user != null,
                request.getStatus().name(),
                request.getCreatedAt()
        );
    }

    private record SurfaceRef(ImSurfaceType surfaceType, Long surfaceId, ImJoinPolicy joinPolicy, Long conversationId,
                              String contextType, Long parentChannelId, ImChannelPo channel, ImGroupPo group) {
    }
}
