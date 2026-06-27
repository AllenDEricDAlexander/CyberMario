package top.egon.mario.im.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.im.po.ImConversationMemberPo;
import top.egon.mario.im.po.ImMembershipPo;
import top.egon.mario.im.po.enums.ImDeliveryMode;
import top.egon.mario.im.po.enums.ImMembershipRole;
import top.egon.mario.im.po.enums.ImMembershipStatus;
import top.egon.mario.im.po.enums.ImSurfaceType;
import top.egon.mario.im.repository.ImConversationMemberRepository;
import top.egon.mario.im.repository.ImMembershipRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MembershipService {

    private final ImMembershipRepository membershipRepository;
    private final ImConversationMemberRepository conversationMemberRepository;

    public MembershipService(ImMembershipRepository membershipRepository,
                             ImConversationMemberRepository conversationMemberRepository) {
        this.membershipRepository = membershipRepository;
        this.conversationMemberRepository = conversationMemberRepository;
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
        return conversationMemberRepository.findByConversationIdAndUserIdAndDeletedFalse(conversationId, userId)
                .map(member -> activateConversationMember(member, now))
                .orElseGet(() -> conversationMemberRepository.saveAndFlush(conversationMember(
                        conversationId, userId, now)));
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
}
