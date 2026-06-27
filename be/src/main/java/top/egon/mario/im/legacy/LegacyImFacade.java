package top.egon.mario.im.legacy;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.mario.im.context.ImPrincipal;
import top.egon.mario.im.po.ImChannelPo;
import top.egon.mario.im.po.ImConversationPo;
import top.egon.mario.im.po.ImGroupPo;
import top.egon.mario.im.po.ImMessagePo;
import top.egon.mario.im.po.ImReadStatePo;
import top.egon.mario.im.service.ImCoreService;

import java.util.Collection;
import java.util.function.Supplier;

@Component
public class LegacyImFacade {

    private static final int RETRY_LIMIT = 3;

    private final ImCoreService imCoreService;
    private final TransactionOperations retryTransactionOperations;

    public LegacyImFacade(ImCoreService imCoreService, PlatformTransactionManager transactionManager) {
        this.imCoreService = imCoreService;
        this.retryTransactionOperations = requiresNew(transactionManager);
    }

    public ImChannelPo ensureChannel(String contextType, Long contextId, String channelType) {
        return retryOnUniqueConflict(() -> imCoreService.ensureChannel(contextType, contextId, channelType));
    }

    public ImGroupPo ensureGroup(Long channelId, String groupType) {
        return retryOnUniqueConflict(() -> imCoreService.ensureGroup(channelId, groupType));
    }

    public ImConversationPo ensureConversation(Long groupId, String scopeType, Long scopeId, String type,
                                               Collection<Long> participantUserIds) {
        return retryOnUniqueConflict(() -> imCoreService.ensureConversation(
                groupId, scopeType, scopeId, type, participantUserIds));
    }

    public ImMessagePo sendMessage(Long conversationId, ImPrincipal sender, String content, String metadata) {
        return retryOnUniqueConflict(() -> imCoreService.sendMessage(conversationId, sender, content, metadata));
    }

    public Page<ImMessagePo> history(Long conversationId, ImPrincipal viewer, Pageable pageRequest) {
        return imCoreService.history(conversationId, viewer, pageRequest);
    }

    public ImReadStatePo markRead(Long conversationId, ImPrincipal viewer, Long messageSeq) {
        return imCoreService.markRead(conversationId, viewer, messageSeq);
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
        throw new IllegalStateException("IM_RETRY_EXHAUSTED");
    }

    private static TransactionOperations requiresNew(PlatformTransactionManager transactionManager) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transactionTemplate;
    }
}
