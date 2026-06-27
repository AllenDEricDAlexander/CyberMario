package top.egon.mario.im.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.im.context.ImContext;
import top.egon.mario.im.context.ImPrincipal;
import top.egon.mario.im.factory.ImEntityFactory;
import top.egon.mario.im.po.ImChannelPo;
import top.egon.mario.im.po.ImConversationMemberPo;
import top.egon.mario.im.po.ImConversationPo;
import top.egon.mario.im.po.ImGroupPo;
import top.egon.mario.im.po.ImMessagePo;
import top.egon.mario.im.po.ImReadStatePo;
import top.egon.mario.im.policy.ImPolicyRegistry;
import top.egon.mario.im.repository.ImChannelRepository;
import top.egon.mario.im.repository.ImConversationMemberRepository;
import top.egon.mario.im.repository.ImConversationRepository;
import top.egon.mario.im.repository.ImMessageRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

@Service
public class ImCoreService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String LEGACY_FACADE_REPLACED = "IM_LEGACY_FACADE_REPLACED";

    private final ImChannelRepository channelRepository;
    private final ImConversationRepository conversationRepository;
    private final ImConversationMemberRepository memberRepository;
    private final ImMessageRepository messageRepository;
    private final ImPolicyRegistry policyRegistry;
    private final ImEntityFactory entityFactory;

    public ImCoreService(ImChannelRepository channelRepository,
                         ImConversationRepository conversationRepository,
                         ImConversationMemberRepository memberRepository,
                         ImMessageRepository messageRepository,
                         ImPolicyRegistry policyRegistry,
                         ImEntityFactory entityFactory) {
        this.channelRepository = channelRepository;
        this.conversationRepository = conversationRepository;
        this.memberRepository = memberRepository;
        this.messageRepository = messageRepository;
        this.policyRegistry = policyRegistry;
        this.entityFactory = entityFactory;
    }

    @Transactional
    public ImChannelPo ensureChannel(String contextType, Long contextId, String channelType) {
        requireText(contextType, "IM_CONTEXT_TYPE_REQUIRED");
        requireId(contextId, "IM_CONTEXT_ID_REQUIRED");
        requireText(channelType, "IM_CHANNEL_TYPE_REQUIRED");
        return channelRepository.findByContextTypeAndContextIdAndChannelKeyAndDeletedFalse(
                        contextType, contextId, channelType)
                .orElseGet(() -> channelRepository.saveAndFlush(entityFactory.channel(
                        contextType, contextId, channelType, Instant.now())));
    }

    @Transactional
    public ImGroupPo ensureGroup(Long channelId, String groupType) {
        requireId(channelId, "IM_CHANNEL_ID_REQUIRED");
        requireText(groupType, "IM_GROUP_TYPE_REQUIRED");
        throw legacyFacadeReplaced();
    }

    @Transactional
    public ImConversationPo ensureConversation(Long groupId, String scopeType, Long scopeId, String type,
                                               Collection<Long> participantUserIds) {
        requireId(groupId, "IM_GROUP_ID_REQUIRED");
        requireText(scopeType, "IM_SCOPE_TYPE_REQUIRED");
        requireId(scopeId, "IM_SCOPE_ID_REQUIRED");
        requireText(type, "IM_CONVERSATION_TYPE_REQUIRED");
        throw legacyFacadeReplaced();
    }

    private ImException legacyFacadeReplaced() {
        return new ImException(LEGACY_FACADE_REPLACED, "Legacy IM facade path was replaced by V30 IM services");
    }

    @Transactional
    public ImMessagePo sendMessage(Long conversationId, ImPrincipal sender, String content, String metadata) {
        ImConversationPo conversation = lockedConversation(conversationId);
        ImPrincipal checkedSender = requirePrincipal(sender);
        Optional<ImConversationMemberPo> senderMember = activeMember(conversation.getId(), checkedSender.userId());
        ImContext context = context(conversation, checkedSender, senderMember.isPresent());
        if (!policyRegistry.resolveSendPolicy(conversation.getContextType()).canSend(context)) {
            throw new ImException("IM_SEND_DENIED");
        }
        String checkedMetadata = metadata(metadata);
        Instant now = Instant.now();
        Long nextSeq = nextMessageSeq(conversation);
        ImMessagePo message = entityFactory.message(conversation.getId(),
                senderMember.map(ImConversationMemberPo::getId).orElse(null), checkedSender.userId(),
                nextSeq, content, checkedMetadata, now);
        ImMessagePo saved = messageRepository.saveAndFlush(message);
        conversation.setMessageSeq(nextSeq);
        conversation.setLastMessageId(saved.getId());
        conversation.setLastMessageAt(now);
        conversation.setLastActiveAt(now);
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<ImMessagePo> history(Long conversationId, ImPrincipal viewer, Pageable pageRequest) {
        requireId(conversationId, "IM_CONVERSATION_ID_REQUIRED");
        Objects.requireNonNull(pageRequest, "pageRequest must not be null");
        ImConversationPo conversation = conversationRepository.findByIdAndDeletedFalse(conversationId)
                .orElseThrow(() -> new ImException("IM_CONVERSATION_NOT_FOUND"));
        ImPrincipal checkedViewer = requirePrincipal(viewer);
        boolean activeMember = memberRepository.existsByConversationIdAndUserIdAndStatusAndDeletedFalse(
                conversation.getId(), checkedViewer.userId(), STATUS_ACTIVE);
        if (!policyRegistry.resolveVisibilityPolicy(conversation.getContextType())
                .canRead(context(conversation, checkedViewer, activeMember))) {
            throw new ImException("IM_HISTORY_FORBIDDEN");
        }
        return messageRepository.findByConversationIdAndDeletedFalseOrderByMessageSeqAsc(conversationId, pageRequest);
    }

    @Transactional
    public ImReadStatePo markRead(Long conversationId, ImPrincipal viewer, Long messageSeq) {
        ImConversationPo conversation = lockedConversation(conversationId);
        ImPrincipal checkedViewer = requirePrincipal(viewer);
        ImConversationMemberPo member = activeMember(conversation.getId(), checkedViewer.userId())
                .orElseThrow(() -> new ImException("IM_MEMBER_REQUIRED"));
        Long targetSeq = Math.max(0L, Math.min(messageSeq == null ? 0L : messageSeq,
                conversation.getMessageSeq() == null ? 0L : conversation.getMessageSeq()));
        Instant now = Instant.now();
        Long currentSeq = member.getLastReadMessageSeq() == null ? 0L : member.getLastReadMessageSeq();
        Long nextSeq = Math.max(currentSeq, targetSeq);
        if (!nextSeq.equals(currentSeq)) {
            member.setLastReadMessageSeq(nextSeq);
            member.setLastActiveAt(now);
        }
        ImReadStatePo readState = new ImReadStatePo();
        readState.setConversationId(conversation.getId());
        readState.setConversationMemberId(member.getId());
        readState.setUserId(checkedViewer.userId());
        readState.setLastReadMessageSeq(nextSeq);
        readState.setLastReadAt(now);
        return readState;
    }

    private ImConversationPo lockedConversation(Long conversationId) {
        requireId(conversationId, "IM_CONVERSATION_ID_REQUIRED");
        return conversationRepository.findLockedByIdAndDeletedFalse(conversationId)
                .orElseThrow(() -> new ImException("IM_CONVERSATION_NOT_FOUND"));
    }

    private Optional<ImConversationMemberPo> activeMember(Long conversationId, Long userId) {
        return memberRepository.findByConversationIdAndUserIdAndStatusAndDeletedFalse(
                conversationId, userId, STATUS_ACTIVE);
    }

    private Long nextMessageSeq(ImConversationPo conversation) {
        Long currentSeq = conversation.getMessageSeq();
        if (currentSeq == null || currentSeq < 0) {
            currentSeq = messageRepository.findTopByConversationIdAndDeletedFalseOrderByMessageSeqDesc(
                            conversation.getId())
                    .map(ImMessagePo::getMessageSeq)
                    .orElse(0L);
        }
        return currentSeq + 1;
    }

    private ImContext context(ImConversationPo conversation, ImPrincipal principal, boolean activeMember) {
        return new ImContext(conversation.getContextType(), conversation.getContextId(),
                conversation.getChannelId(), conversation.getGroupId(), conversation.getId(),
                conversation.getScopeType(), conversation.getScopeId(), conversation.getConversationType(),
                conversation.getParticipantKey(), principal, activeMember);
    }

    private ImPrincipal requirePrincipal(ImPrincipal principal) {
        return Objects.requireNonNull(principal, "principal must not be null");
    }

    private void requireText(String value, String errorCode) {
        if (!StringUtils.hasText(value)) {
            throw new ImException(errorCode);
        }
    }

    private void requireId(Long value, String errorCode) {
        if (value == null) {
            throw new ImException(errorCode);
        }
    }

    private String metadata(String metadata) {
        return StringUtils.hasText(metadata) ? metadata : "{}";
    }
}
