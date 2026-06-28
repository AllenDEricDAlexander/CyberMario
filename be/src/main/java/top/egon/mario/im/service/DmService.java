package top.egon.mario.im.service;

import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import top.egon.mario.im.service.ImException;
import top.egon.mario.im.facade.dto.command.BlockUserCommand;
import top.egon.mario.im.facade.dto.command.OpenDmCommand;
import top.egon.mario.im.facade.dto.view.ConversationView;
import top.egon.mario.im.facade.mapper.ImFacadeMapper;
import top.egon.mario.im.po.ImConversationPo;
import top.egon.mario.im.po.ImDmBlockPo;
import top.egon.mario.im.po.ImDmPairPo;
import top.egon.mario.im.po.enums.ImConversationStatus;
import top.egon.mario.im.po.enums.ImConversationType;
import top.egon.mario.im.po.enums.ImGovernanceStatus;
import top.egon.mario.im.po.enums.ImSurfaceType;
import top.egon.mario.im.policy.ImPrincipal;
import top.egon.mario.im.repository.ImConversationRepository;
import top.egon.mario.im.repository.ImDmBlockRepository;
import top.egon.mario.im.repository.ImDmPairRepository;

import java.time.Instant;
import java.util.function.Supplier;

@Service
public class DmService {

    private static final long PAIR_CONVERSATION_PLACEHOLDER = 0L;
    private static final int RETRY_LIMIT = 3;

    private final ImDmPairRepository dmPairRepository;
    private final ImDmBlockRepository dmBlockRepository;
    private final ImConversationRepository conversationRepository;
    private final MembershipService membershipService;
    private final TransactionOperations retryTransactionOperations;
    private final ImFacadeMapper mapper = new ImFacadeMapper();

    public DmService(ImDmPairRepository dmPairRepository,
                     ImDmBlockRepository dmBlockRepository,
                     ImConversationRepository conversationRepository,
                     MembershipService membershipService,
                     PlatformTransactionManager transactionManager) {
        this.dmPairRepository = dmPairRepository;
        this.dmBlockRepository = dmBlockRepository;
        this.conversationRepository = conversationRepository;
        this.membershipService = membershipService;
        this.retryTransactionOperations = requiresNew(transactionManager);
    }

    public ConversationView openDm(OpenDmCommand command) {
        return retryOnUniqueConflict(() -> openDmInTransaction(command));
    }

    public void block(BlockUserCommand command) {
        retryOnUniqueConflict(() -> {
            blockInTransaction(command);
            return null;
        });
    }

    public void unblock(BlockUserCommand command) {
        retryOnUniqueConflict(() -> {
            unblockInTransaction(command);
            return null;
        });
    }

    private ConversationView openDmInTransaction(OpenDmCommand command) {
        if (command == null) {
            throw new ImException("IM_DM_COMMAND_REQUIRED");
        }
        ImPrincipal principal = requirePrincipal(command.principal());
        Long targetUserId = requireTargetUserId(command.targetUserId());
        requireDifferentUsers(principal.userId(), targetUserId);

        PairUsers pairUsers = pairUsers(principal.userId(), targetUserId);
        ImDmPairPo pair = ensurePair(pairUsers, requireContextType(principal), Instant.now());
        ImConversationPo conversation = requireConversation(pair.getConversationId());
        return mapper.toConversationView(conversation);
    }

    private void blockInTransaction(BlockUserCommand command) {
        if (command == null) {
            throw new ImException("IM_DM_BLOCK_COMMAND_REQUIRED");
        }
        ImPrincipal principal = requirePrincipal(command.principal());
        Long targetUserId = requireTargetUserId(command.targetUserId());
        requireDifferentUsers(principal.userId(), targetUserId);

        PairUsers pairUsers = pairUsers(principal.userId(), targetUserId);
        ImDmPairPo pair = ensurePair(pairUsers, requireContextType(principal), Instant.now());
        ImDmBlockPo block = dmBlockRepository
                .findByBlockerUserIdAndBlockedUserIdAndDeletedFalse(principal.userId(), targetUserId)
                .orElseGet(ImDmBlockPo::new);
        block.setBlockerUserId(principal.userId());
        block.setBlockedUserId(targetUserId);
        block.setStatus(ImGovernanceStatus.ACTIVE);
        block.setReason(reason(command.reason()));
        block.setMetadataJson("{}");
        dmBlockRepository.saveAndFlush(block);

        pair.setFrozen(true);
        dmPairRepository.saveAndFlush(pair);
    }

    private void unblockInTransaction(BlockUserCommand command) {
        if (command == null) {
            throw new ImException("IM_DM_BLOCK_COMMAND_REQUIRED");
        }
        ImPrincipal principal = requirePrincipal(command.principal());
        Long targetUserId = requireTargetUserId(command.targetUserId());
        requireDifferentUsers(principal.userId(), targetUserId);

        PairUsers pairUsers = pairUsers(principal.userId(), targetUserId);
        ImDmPairPo pair = ensurePair(pairUsers, requireContextType(principal), Instant.now());
        dmBlockRepository.findByBlockerUserIdAndBlockedUserIdAndDeletedFalse(principal.userId(), targetUserId)
                .ifPresent(block -> {
                    block.setStatus(ImGovernanceStatus.INACTIVE);
                    dmBlockRepository.saveAndFlush(block);
                });
        if (dmBlockRepository.countActiveBetween(pairUsers.userLoId(), pairUsers.userHiId()) == 0) {
            pair.setFrozen(false);
            dmPairRepository.saveAndFlush(pair);
        }
    }

    private ImDmPairPo ensurePair(PairUsers pairUsers, String contextType, Instant now) {
        return dmPairRepository.findLockedByUserLoIdAndUserHiIdAndDeletedFalse(
                        pairUsers.userLoId(), pairUsers.userHiId())
                .map(pair -> ensurePairConversation(pair, contextType, now))
                .orElseGet(() -> createPair(pairUsers, contextType, now));
    }

    private ImDmPairPo createPair(PairUsers pairUsers, String contextType, Instant now) {
        ImDmPairPo pair = new ImDmPairPo();
        pair.setUserLoId(pairUsers.userLoId());
        pair.setUserHiId(pairUsers.userHiId());
        pair.setConversationId(PAIR_CONVERSATION_PLACEHOLDER);
        pair.setFrozen(false);
        pair.setMetadataJson("{}");
        pair = dmPairRepository.saveAndFlush(pair);
        return ensurePairConversation(pair, contextType, now);
    }

    private ImDmPairPo ensurePairConversation(ImDmPairPo pair, String contextType, Instant now) {
        ImConversationPo conversation = conversationRepository
                .findByOwnerSurfaceTypeAndOwnerSurfaceIdAndConversationTypeAndDeletedFalse(
                        ImSurfaceType.DM_PAIR, pair.getId(), ImConversationType.DM)
                .orElseGet(() -> conversationRepository.saveAndFlush(conversation(pair, contextType, now)));
        if (!conversation.getId().equals(pair.getConversationId())) {
            pair.setConversationId(conversation.getId());
            dmPairRepository.saveAndFlush(pair);
        }
        membershipService.ensureConversationMember(conversation.getId(), pair.getUserLoId(), now);
        membershipService.ensureConversationMember(conversation.getId(), pair.getUserHiId(), now);
        return pair;
    }

    private ImConversationPo conversation(ImDmPairPo pair, String contextType, Instant now) {
        ImConversationPo conversation = new ImConversationPo();
        conversation.setConversationType(ImConversationType.DM);
        conversation.setOwnerSurfaceType(ImSurfaceType.DM_PAIR);
        conversation.setOwnerSurfaceId(pair.getId());
        conversation.setContextType(contextType);
        conversation.setContextId(null);
        conversation.setMessageSeq(0L);
        conversation.setLastActiveAt(now);
        conversation.setStatus(ImConversationStatus.ACTIVE);
        conversation.setMetadataJson("{}");
        return conversation;
    }

    private ImConversationPo requireConversation(Long conversationId) {
        return conversationRepository.findByIdAndDeletedFalse(conversationId)
                .orElseThrow(() -> new ImException("IM_DM_CONVERSATION_NOT_FOUND"));
    }

    private ImPrincipal requirePrincipal(ImPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new ImException("IM_PRINCIPAL_REQUIRED");
        }
        return principal;
    }

    private Long requireTargetUserId(Long targetUserId) {
        if (targetUserId == null) {
            throw new ImException("IM_DM_TARGET_REQUIRED");
        }
        return targetUserId;
    }

    private String requireContextType(ImPrincipal principal) {
        if (!StringUtils.hasText(principal.contextType())) {
            throw new ImException("IM_CONTEXT_TYPE_REQUIRED");
        }
        return principal.contextType().trim();
    }

    private void requireDifferentUsers(Long userId, Long targetUserId) {
        if (userId.equals(targetUserId)) {
            throw new ImException("IM_DM_SELF_DENIED");
        }
    }

    private PairUsers pairUsers(Long userId, Long targetUserId) {
        if (userId < targetUserId) {
            return new PairUsers(userId, targetUserId);
        }
        return new PairUsers(targetUserId, userId);
    }

    private String reason(String reason) {
        return StringUtils.hasText(reason) ? reason.trim() : null;
    }

    private <T> T retryOnUniqueConflict(Supplier<T> action) {
        for (int attempt = 1; attempt <= RETRY_LIMIT; attempt++) {
            try {
                return retryTransactionOperations.execute(status -> action.get());
            } catch (DataIntegrityViolationException ex) {
                if (attempt == RETRY_LIMIT) {
                    throw ex;
                }
            }
        }
        throw new IllegalStateException("IM_DM_RETRY_EXHAUSTED");
    }

    private static TransactionOperations requiresNew(PlatformTransactionManager transactionManager) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transactionTemplate;
    }

    private record PairUsers(Long userLoId, Long userHiId) {
    }
}
