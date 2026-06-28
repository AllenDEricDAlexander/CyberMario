package top.egon.mario.im.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.im.service.ImException;
import top.egon.mario.im.facade.dto.command.AnnounceCommand;
import top.egon.mario.im.facade.dto.command.BanUserCommand;
import top.egon.mario.im.facade.dto.command.GlobalMuteCommand;
import top.egon.mario.im.facade.dto.command.MuteUserCommand;
import top.egon.mario.im.po.ImBanPo;
import top.egon.mario.im.po.ImChannelPo;
import top.egon.mario.im.po.ImGlobalMutePo;
import top.egon.mario.im.po.ImGroupPo;
import top.egon.mario.im.po.ImMembershipPo;
import top.egon.mario.im.po.enums.ImGlobalMuteScopeType;
import top.egon.mario.im.po.enums.ImGovernanceStatus;
import top.egon.mario.im.po.enums.ImMembershipRole;
import top.egon.mario.im.po.enums.ImMembershipStatus;
import top.egon.mario.im.po.enums.ImSurfaceStatus;
import top.egon.mario.im.po.enums.ImSurfaceType;
import top.egon.mario.im.policy.ImPrincipal;
import top.egon.mario.im.repository.ImBanRepository;
import top.egon.mario.im.repository.ImChannelRepository;
import top.egon.mario.im.repository.ImConversationMemberRepository;
import top.egon.mario.im.repository.ImGlobalMuteRepository;
import top.egon.mario.im.repository.ImGroupRepository;
import top.egon.mario.im.repository.ImMembershipRepository;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class GovernanceService {

    private static final long PLATFORM_SCOPE_ID = 0L;

    private final ImMembershipRepository membershipRepository;
    private final ImConversationMemberRepository conversationMemberRepository;
    private final ImChannelRepository channelRepository;
    private final ImGroupRepository groupRepository;
    private final ImGlobalMuteRepository globalMuteRepository;
    private final ImBanRepository banRepository;

    public GovernanceService(ImMembershipRepository membershipRepository,
                             ImConversationMemberRepository conversationMemberRepository,
                             ImChannelRepository channelRepository,
                             ImGroupRepository groupRepository,
                             ImGlobalMuteRepository globalMuteRepository,
                             ImBanRepository banRepository) {
        this.membershipRepository = membershipRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.channelRepository = channelRepository;
        this.groupRepository = groupRepository;
        this.globalMuteRepository = globalMuteRepository;
        this.banRepository = banRepository;
    }

    @Transactional
    public void mute(MuteUserCommand command) {
        if (command == null) {
            throw new ImException("IM_MUTE_COMMAND_REQUIRED");
        }
        ImPrincipal principal = requirePrincipal(command.principal());
        ImSurfaceType surfaceType = surfaceType(command.surfaceType());
        Long surfaceId = requireId(command.surfaceId(), "IM_SURFACE_ID_REQUIRED");
        Long targetUserId = requireId(command.userId(), "IM_TARGET_USER_ID_REQUIRED");
        SurfaceRef surface = requireLockedSurface(surfaceType, surfaceId);
        requireGovernanceActor(surface, principal.userId());

        ImMembershipPo target = membershipRepository
                .findBySurfaceTypeAndSurfaceIdAndUserIdAndStatusAndDeletedFalse(
                        surface.surfaceType(), surface.surfaceId(), targetUserId, ImMembershipStatus.ACTIVE)
                .orElseThrow(() -> new ImException("IM_MEMBER_NOT_ACTIVE"));
        target.setMutedUntil(command.mutedUntil());
        membershipRepository.saveAndFlush(target);
    }

    @Transactional
    public void globalMute(GlobalMuteCommand command) {
        if (command == null) {
            throw new ImException("IM_GLOBAL_MUTE_COMMAND_REQUIRED");
        }
        requirePrincipal(command.principal());
        Long targetUserId = requireId(command.userId(), "IM_TARGET_USER_ID_REQUIRED");
        ImGlobalMuteScopeType scopeType = globalMuteScopeType(command.scopeType());
        Long scopeId = globalMuteScopeId(scopeType, command.scopeId());

        List<ImGlobalMutePo> existing = globalMuteRepository
                .findByUserIdAndScopeTypeAndScopeIdAndDeletedFalseOrderByIdAsc(targetUserId, scopeType, scopeId);
        ImGlobalMutePo mute = existing.isEmpty() ? new ImGlobalMutePo() : existing.get(0);
        applyGlobalMute(mute, targetUserId, scopeType, scopeId, command.mutedUntil(), command.reason());
        deactivateDuplicateGlobalMutes(existing, mute);
        globalMuteRepository.saveAndFlush(mute);
    }

    @Transactional
    public void announce(AnnounceCommand command) {
        if (command == null) {
            throw new ImException("IM_ANNOUNCE_COMMAND_REQUIRED");
        }
        ImPrincipal principal = requirePrincipal(command.principal());
        ImSurfaceType surfaceType = surfaceType(command.surfaceType());
        Long surfaceId = requireId(command.surfaceId(), "IM_SURFACE_ID_REQUIRED");
        SurfaceRef surface = requireLockedSurface(surfaceType, surfaceId);
        requireGovernanceActor(surface, principal.userId());

        if (surface.channel() != null) {
            surface.channel().setAnnouncement(announcement(command.announcement()));
            channelRepository.saveAndFlush(surface.channel());
            return;
        }
        surface.group().setAnnouncement(announcement(command.announcement()));
        groupRepository.saveAndFlush(surface.group());
    }

    @Transactional
    public void ban(BanUserCommand command) {
        if (command == null) {
            throw new ImException("IM_BAN_COMMAND_REQUIRED");
        }
        ImPrincipal principal = requirePrincipal(command.principal());
        ImSurfaceType surfaceType = surfaceType(command.surfaceType());
        Long surfaceId = requireId(command.surfaceId(), "IM_SURFACE_ID_REQUIRED");
        Long targetUserId = requireId(command.userId(), "IM_TARGET_USER_ID_REQUIRED");
        SurfaceRef surface = requireLockedSurface(surfaceType, surfaceId);
        requireGovernanceActor(surface, principal.userId());

        Instant now = Instant.now();
        ImMembershipPo membership = membershipRepository
                .findBySurfaceTypeAndSurfaceIdAndUserIdAndDeletedFalse(
                        surface.surfaceType(), surface.surfaceId(), targetUserId)
                .orElseGet(() -> memberMembership(surface, targetUserId, now));
        boolean wasActive = ImMembershipStatus.ACTIVE.equals(membership.getStatus());
        membership.setStatus(ImMembershipStatus.BANNED);
        membershipRepository.saveAndFlush(membership);

        conversationMemberRepository.findByConversationIdAndUserIdAndDeletedFalse(
                        surface.conversationId(), targetUserId)
                .ifPresent(member -> {
                    member.setStatus(ImMembershipStatus.LEFT);
                    conversationMemberRepository.saveAndFlush(member);
                });
        List<ImBanPo> activeBans = banRepository.findActiveBans(
                surface.surfaceType(), surface.surfaceId(), targetUserId, ImGovernanceStatus.ACTIVE, now);
        ImBanPo ban = activeBans.isEmpty() ? new ImBanPo() : activeBans.get(0);
        applyBan(ban, surface, targetUserId, principal.userId(), command.reason());
        deactivateDuplicateBans(activeBans, ban);
        banRepository.saveAndFlush(ban);
        if (wasActive) {
            decrementMemberCount(surface);
        }
    }

    private SurfaceRef requireLockedSurface(ImSurfaceType surfaceType, Long surfaceId) {
        if (ImSurfaceType.CHANNEL.equals(surfaceType)) {
            ImChannelPo channel = channelRepository.findLockedByIdAndDeletedFalse(surfaceId)
                    .filter(candidate -> ImSurfaceStatus.ACTIVE.equals(candidate.getStatus()))
                    .orElseThrow(() -> new ImException("IM_CHANNEL_NOT_FOUND"));
            if (channel.getMainConversationId() == null) {
                throw new ImException("IM_SURFACE_CONVERSATION_REQUIRED");
            }
            return new SurfaceRef(surfaceType, channel.getId(), channel.getMainConversationId(), channel, null);
        }
        if (ImSurfaceType.GROUP.equals(surfaceType)) {
            ImGroupPo group = groupRepository.findLockedByIdAndDeletedFalse(surfaceId)
                    .filter(candidate -> ImSurfaceStatus.ACTIVE.equals(candidate.getStatus()))
                    .orElseThrow(() -> new ImException("IM_GROUP_NOT_FOUND"));
            if (group.getConversationId() == null) {
                throw new ImException("IM_SURFACE_CONVERSATION_REQUIRED");
            }
            return new SurfaceRef(surfaceType, group.getId(), group.getConversationId(), null, group);
        }
        throw new ImException("IM_SURFACE_TYPE_UNSUPPORTED");
    }

    private void requireGovernanceActor(SurfaceRef surface, Long userId) {
        membershipRepository.findBySurfaceTypeAndSurfaceIdAndUserIdAndStatusAndDeletedFalse(
                        surface.surfaceType(), surface.surfaceId(), userId, ImMembershipStatus.ACTIVE)
                .filter(membership -> ImMembershipRole.OWNER.equals(membership.getMemberRole())
                        || ImMembershipRole.ADMIN.equals(membership.getMemberRole()))
                .orElseThrow(() -> new ImException("IM_GOVERNANCE_ACTOR_REQUIRED"));
    }

    private ImMembershipPo memberMembership(SurfaceRef surface, Long userId, Instant now) {
        ImMembershipPo membership = new ImMembershipPo();
        membership.setSurfaceType(surface.surfaceType());
        membership.setSurfaceId(surface.surfaceId());
        membership.setUserId(userId);
        membership.setMemberRole(ImMembershipRole.MEMBER);
        membership.setStatus(ImMembershipStatus.BANNED);
        membership.setJoinedAt(now);
        membership.setMetadataJson("{}");
        return membership;
    }

    private void applyGlobalMute(ImGlobalMutePo mute, Long userId, ImGlobalMuteScopeType scopeType, Long scopeId,
                                 Instant mutedUntil, String reason) {
        mute.setUserId(userId);
        mute.setScopeType(scopeType);
        mute.setScopeId(scopeId);
        mute.setExpiresAt(mutedUntil);
        mute.setReason(optionalText(reason));
        mute.setStatus(ImGovernanceStatus.ACTIVE);
        mute.setMetadataJson("{}");
    }

    private void deactivateDuplicateGlobalMutes(List<ImGlobalMutePo> existing, ImGlobalMutePo mute) {
        existing.stream()
                .filter(candidate -> candidate != mute)
                .forEach(candidate -> candidate.setStatus(ImGovernanceStatus.INACTIVE));
        globalMuteRepository.saveAll(existing);
    }

    private void applyBan(ImBanPo ban, SurfaceRef surface, Long userId, Long actorUserId, String reason) {
        ban.setSurfaceType(surface.surfaceType());
        ban.setSurfaceId(surface.surfaceId());
        ban.setUserId(userId);
        ban.setActorUserId(actorUserId);
        ban.setReason(optionalText(reason));
        ban.setStatus(ImGovernanceStatus.ACTIVE);
        ban.setMetadataJson("{}");
    }

    private void deactivateDuplicateBans(List<ImBanPo> activeBans, ImBanPo ban) {
        activeBans.stream()
                .filter(candidate -> candidate != ban)
                .forEach(candidate -> candidate.setStatus(ImGovernanceStatus.INACTIVE));
        banRepository.saveAll(activeBans);
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

    private ImGlobalMuteScopeType globalMuteScopeType(String value) {
        String scopeType = requireText(value, "IM_GLOBAL_MUTE_SCOPE_TYPE_REQUIRED");
        try {
            return ImGlobalMuteScopeType.valueOf(scopeType.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ImException("IM_GLOBAL_MUTE_SCOPE_TYPE_INVALID", scopeType);
        }
    }

    private Long globalMuteScopeId(ImGlobalMuteScopeType scopeType, Long scopeId) {
        if (ImGlobalMuteScopeType.PLATFORM.equals(scopeType)) {
            return PLATFORM_SCOPE_ID;
        }
        return requireId(scopeId, "IM_GLOBAL_MUTE_SCOPE_ID_REQUIRED");
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

    private String optionalText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String announcement(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private int memberCount(Integer value) {
        return value == null ? 0 : value;
    }

    private record SurfaceRef(ImSurfaceType surfaceType, Long surfaceId, Long conversationId,
                              ImChannelPo channel, ImGroupPo group) {
    }
}
