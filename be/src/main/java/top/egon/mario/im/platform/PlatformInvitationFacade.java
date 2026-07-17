package top.egon.mario.im.platform;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.im.platform.dto.PlatformInvitationView;
import top.egon.mario.im.po.ImChannelPo;
import top.egon.mario.im.po.ImGroupPo;
import top.egon.mario.im.po.ImMembershipPo;
import top.egon.mario.im.po.ImSurfaceInvitationPo;
import top.egon.mario.im.po.enums.ImMembershipRole;
import top.egon.mario.im.po.enums.ImMembershipStatus;
import top.egon.mario.im.po.enums.ImSurfaceInvitationStatus;
import top.egon.mario.im.po.enums.ImSurfaceStatus;
import top.egon.mario.im.po.enums.ImSurfaceType;
import top.egon.mario.im.policy.ImPrincipal;
import top.egon.mario.im.repository.ImChannelRepository;
import top.egon.mario.im.repository.ImGroupRepository;
import top.egon.mario.im.repository.ImMembershipRepository;
import top.egon.mario.im.repository.ImSurfaceInvitationRepository;
import top.egon.mario.im.service.ImException;
import top.egon.mario.im.service.MembershipService;
import top.egon.mario.rbac.application.RbacUserDirectoryFacade;
import top.egon.mario.rbac.dto.response.UserDirectoryItemResponse;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class PlatformInvitationFacade {

    private static final int MAX_PAGE_SIZE = 100;

    private final ImSurfaceInvitationRepository invitationRepository;
    private final ImMembershipRepository membershipRepository;
    private final ImChannelRepository channelRepository;
    private final ImGroupRepository groupRepository;
    private final MembershipService membershipService;
    private final RbacUserDirectoryFacade userDirectoryFacade;

    public PlatformInvitationFacade(ImSurfaceInvitationRepository invitationRepository,
                                    ImMembershipRepository membershipRepository,
                                    ImChannelRepository channelRepository,
                                    ImGroupRepository groupRepository,
                                    MembershipService membershipService,
                                    RbacUserDirectoryFacade userDirectoryFacade) {
        this.invitationRepository = invitationRepository;
        this.membershipRepository = membershipRepository;
        this.channelRepository = channelRepository;
        this.groupRepository = groupRepository;
        this.membershipService = membershipService;
        this.userDirectoryFacade = userDirectoryFacade;
    }

    @Transactional
    public PlatformInvitationView invite(ImPrincipal principal,
                                         String surfaceTypeValue,
                                         Long surfaceId,
                                         Long inviteeUserId,
                                         String message) {
        ImPrincipal caller = requirePrincipal(principal);
        ImSurfaceType surfaceType = surfaceType(surfaceTypeValue);
        SurfaceRef surface = requireSurface(surfaceType, requireId(surfaceId, "IM_SURFACE_ID_REQUIRED"), true);
        requireManager(surfaceType, surface.id(), caller.userId());
        Long inviteeId = requireId(inviteeUserId, "IM_INVITEE_USER_ID_REQUIRED");
        if (caller.userId().equals(inviteeId)) {
            throw new ImException("IM_INVITATION_SELF_DENIED");
        }
        userDirectoryFacade.findEnabledById(inviteeId)
                .orElseThrow(() -> new ImException("IM_INVITEE_USER_NOT_FOUND"));
        requireParentMembership(surface, inviteeId);
        if (membershipRepository.findBySurfaceTypeAndSurfaceIdAndUserIdAndStatusAndDeletedFalse(
                surfaceType, surface.id(), inviteeId, ImMembershipStatus.ACTIVE).isPresent()) {
            throw new ImException("IM_MEMBER_ALREADY_ACTIVE");
        }

        ImSurfaceInvitationPo invitation = invitationRepository
                .findLockedByTarget(surfaceType, surface.id(), inviteeId)
                .orElseGet(ImSurfaceInvitationPo::new);
        invitation.setSurfaceType(surfaceType);
        invitation.setSurfaceId(surface.id());
        invitation.setInviterUserId(caller.userId());
        invitation.setInviteeUserId(inviteeId);
        invitation.setStatus(ImSurfaceInvitationStatus.PENDING);
        invitation.setMessage(optionalMessage(message));
        invitation.setRespondedAt(null);
        invitation.setMetadataJson("{}");
        invitation = invitationRepository.saveAndFlush(invitation);
        return view(invitation, surface, caller.userId(), null);
    }

    @Transactional(readOnly = true)
    public Page<PlatformInvitationView> listIncoming(ImPrincipal principal, int page, int size) {
        ImPrincipal caller = requirePrincipal(principal);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        Page<ImSurfaceInvitationPo> invitations = invitationRepository
                .findByInviteeUserIdAndStatusAndDeletedFalseOrderByCreatedAtDescIdDesc(
                        caller.userId(), ImSurfaceInvitationStatus.PENDING, pageable);
        Map<Long, UserDirectoryItemResponse> inviters = userDirectoryFacade.findEnabledByIds(
                invitations.getContent().stream().map(ImSurfaceInvitationPo::getInviterUserId).toList());
        Map<SurfaceKey, SurfaceRef> surfaces = new LinkedHashMap<>();
        return new PageImpl<>(invitations.getContent().stream()
                .map(invitation -> {
                    SurfaceKey key = new SurfaceKey(invitation.getSurfaceType(), invitation.getSurfaceId());
                    SurfaceRef surface = surfaces.computeIfAbsent(key,
                            ignored -> requireSurface(key.surfaceType(), key.surfaceId(), false));
                    UserDirectoryItemResponse inviter = inviters.get(invitation.getInviterUserId());
                    return view(invitation, surface, invitation.getInviterUserId(), inviter);
                })
                .toList(), pageable, invitations.getTotalElements());
    }

    @Transactional
    public PlatformInvitationView accept(ImPrincipal principal, Long invitationId) {
        ImPrincipal caller = requirePrincipal(principal);
        ImSurfaceInvitationPo invitation = requirePendingInvitation(invitationId, caller.userId());
        SurfaceRef surface = requireSurface(invitation.getSurfaceType(), invitation.getSurfaceId(), true);
        requireParentMembership(surface, caller.userId());
        membershipService.acceptInvitation(invitation.getSurfaceType(), invitation.getSurfaceId(), caller.userId());
        invitation.setStatus(ImSurfaceInvitationStatus.ACCEPTED);
        invitation.setRespondedAt(Instant.now());
        invitationRepository.saveAndFlush(invitation);
        return view(invitation, surface, invitation.getInviterUserId(), null);
    }

    @Transactional
    public PlatformInvitationView reject(ImPrincipal principal, Long invitationId) {
        ImPrincipal caller = requirePrincipal(principal);
        ImSurfaceInvitationPo invitation = requirePendingInvitation(invitationId, caller.userId());
        SurfaceRef surface = requireSurface(invitation.getSurfaceType(), invitation.getSurfaceId(), false);
        invitation.setStatus(ImSurfaceInvitationStatus.REJECTED);
        invitation.setRespondedAt(Instant.now());
        invitationRepository.saveAndFlush(invitation);
        return view(invitation, surface, invitation.getInviterUserId(), null);
    }

    @Transactional
    public void transferOwnership(ImPrincipal principal,
                                  String surfaceTypeValue,
                                  Long surfaceId,
                                  Long newOwnerUserId) {
        ImPrincipal caller = requirePrincipal(principal);
        ImSurfaceType surfaceType = surfaceType(surfaceTypeValue);
        SurfaceRef surface = requireSurface(surfaceType, requireId(surfaceId, "IM_SURFACE_ID_REQUIRED"), true);
        ImMembershipPo currentOwner = membershipRepository
                .findBySurfaceTypeAndSurfaceIdAndUserIdAndStatusAndDeletedFalse(
                        surfaceType, surface.id(), caller.userId(), ImMembershipStatus.ACTIVE)
                .filter(membership -> ImMembershipRole.OWNER.equals(membership.getMemberRole()))
                .orElseThrow(() -> new ImException("IM_SURFACE_OWNER_REQUIRED"));
        Long targetUserId = requireId(newOwnerUserId, "IM_NEW_OWNER_USER_ID_REQUIRED");
        if (caller.userId().equals(targetUserId)) {
            throw new ImException("IM_NEW_OWNER_MUST_DIFFER");
        }
        ImMembershipPo newOwner = membershipRepository
                .findBySurfaceTypeAndSurfaceIdAndUserIdAndStatusAndDeletedFalse(
                        surfaceType, surface.id(), targetUserId, ImMembershipStatus.ACTIVE)
                .orElseThrow(() -> new ImException("IM_NEW_OWNER_MEMBERSHIP_REQUIRED"));
        requireParentMembership(surface, targetUserId);

        currentOwner.setMemberRole(ImMembershipRole.ADMIN);
        newOwner.setMemberRole(ImMembershipRole.OWNER);
        membershipRepository.save(currentOwner);
        membershipRepository.saveAndFlush(newOwner);
        if (surface.channel() != null) {
            surface.channel().setOwnerUserId(targetUserId);
            channelRepository.saveAndFlush(surface.channel());
        } else {
            surface.group().setOwnerUserId(targetUserId);
            groupRepository.saveAndFlush(surface.group());
        }
    }

    private ImSurfaceInvitationPo requirePendingInvitation(Long invitationId, Long inviteeUserId) {
        ImSurfaceInvitationPo invitation = invitationRepository
                .findLockedById(requireId(invitationId, "IM_INVITATION_ID_REQUIRED"))
                .orElseThrow(() -> new ImException("IM_INVITATION_NOT_FOUND"));
        if (!inviteeUserId.equals(invitation.getInviteeUserId())) {
            throw new ImException("IM_INVITATION_INVITEE_REQUIRED");
        }
        if (!ImSurfaceInvitationStatus.PENDING.equals(invitation.getStatus())) {
            throw new ImException("IM_INVITATION_NOT_PENDING");
        }
        return invitation;
    }

    private void requireManager(ImSurfaceType surfaceType, Long surfaceId, Long userId) {
        membershipRepository.findBySurfaceTypeAndSurfaceIdAndUserIdAndStatusAndDeletedFalse(
                        surfaceType, surfaceId, userId, ImMembershipStatus.ACTIVE)
                .filter(membership -> ImMembershipRole.OWNER.equals(membership.getMemberRole())
                        || ImMembershipRole.ADMIN.equals(membership.getMemberRole()))
                .orElseThrow(() -> new ImException("IM_SURFACE_MANAGEMENT_REQUIRED"));
    }

    private void requireParentMembership(SurfaceRef surface, Long userId) {
        if (surface.channelId() == null) {
            return;
        }
        membershipRepository.findBySurfaceTypeAndSurfaceIdAndUserIdAndStatusAndDeletedFalse(
                        ImSurfaceType.CHANNEL, surface.channelId(), userId, ImMembershipStatus.ACTIVE)
                .orElseThrow(() -> new ImException("IM_PARENT_CHANNEL_MEMBERSHIP_REQUIRED"));
    }

    private SurfaceRef requireSurface(ImSurfaceType surfaceType, Long surfaceId, boolean requireActive) {
        if (ImSurfaceType.CHANNEL.equals(surfaceType)) {
            ImChannelPo channel = channelRepository.findByIdAndDeletedFalse(surfaceId)
                    .filter(candidate -> PlatformRoomFacade.PLATFORM_CONTEXT_TYPE.equals(candidate.getContextType()))
                    .filter(candidate -> !requireActive || ImSurfaceStatus.ACTIVE.equals(candidate.getStatus()))
                    .orElseThrow(() -> new ImException("IM_CHANNEL_NOT_FOUND"));
            return new SurfaceRef(surfaceType, channel.getId(), null, channel.getName(), channel, null);
        }
        ImGroupPo group = groupRepository.findByIdAndDeletedFalse(surfaceId)
                .filter(candidate -> PlatformRoomFacade.PLATFORM_CONTEXT_TYPE.equals(candidate.getContextType()))
                .filter(candidate -> !requireActive || ImSurfaceStatus.ACTIVE.equals(candidate.getStatus()))
                .orElseThrow(() -> new ImException("IM_GROUP_NOT_FOUND"));
        return new SurfaceRef(surfaceType, group.getId(), group.getChannelId(), group.getName(), null, group);
    }

    private PlatformInvitationView view(ImSurfaceInvitationPo invitation,
                                        SurfaceRef surface,
                                        Long inviterUserId,
                                        UserDirectoryItemResponse inviter) {
        UserDirectoryItemResponse resolvedInviter = inviter == null
                ? userDirectoryFacade.findEnabledById(inviterUserId).orElse(null)
                : inviter;
        return new PlatformInvitationView(
                invitation.getId(),
                invitation.getSurfaceType().name(),
                invitation.getSurfaceId(),
                surface.channelId(),
                surface.name(),
                inviterUserId,
                resolvedInviter == null ? "用户 " + inviterUserId : resolvedInviter.displayName(),
                invitation.getStatus().name(),
                invitation.getMessage(),
                invitation.getCreatedAt(),
                invitation.getRespondedAt()
        );
    }

    private ImPrincipal requirePrincipal(ImPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new ImException("IM_PRINCIPAL_REQUIRED");
        }
        return principal;
    }

    private ImSurfaceType surfaceType(String value) {
        if (!StringUtils.hasText(value)) {
            throw new ImException("IM_SURFACE_TYPE_REQUIRED");
        }
        try {
            ImSurfaceType type = ImSurfaceType.valueOf(value.trim().toUpperCase(Locale.ROOT));
            if (ImSurfaceType.DM_PAIR.equals(type)) {
                throw new ImException("IM_SURFACE_TYPE_UNSUPPORTED");
            }
            return type;
        } catch (IllegalArgumentException ex) {
            throw new ImException("IM_SURFACE_TYPE_INVALID", value);
        }
    }

    private Long requireId(Long value, String code) {
        if (value == null) {
            throw new ImException(code);
        }
        return value;
    }

    private String optionalMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return "";
        }
        String value = message.trim();
        if (value.length() > 512) {
            throw new ImException("IM_INVITATION_MESSAGE_TOO_LONG");
        }
        return value;
    }

    private record SurfaceKey(ImSurfaceType surfaceType, Long surfaceId) {
    }

    private record SurfaceRef(ImSurfaceType surfaceType,
                              Long id,
                              Long channelId,
                              String name,
                              ImChannelPo channel,
                              ImGroupPo group) {
    }
}
