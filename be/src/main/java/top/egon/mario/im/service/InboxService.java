package top.egon.mario.im.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.im.facade.ImException;
import top.egon.mario.im.po.ImConversationMemberPo;
import top.egon.mario.im.po.ImConversationPo;
import top.egon.mario.im.po.ImInboxPo;
import top.egon.mario.im.po.enums.ImConversationType;
import top.egon.mario.im.po.enums.ImMembershipStatus;
import top.egon.mario.im.repository.ImConversationMemberRepository;
import top.egon.mario.im.repository.ImConversationRepository;
import top.egon.mario.im.repository.ImInboxRepository;

import java.util.List;

@Service
public class InboxService {

    private final ImConversationRepository conversationRepository;
    private final ImConversationMemberRepository conversationMemberRepository;
    private final ImInboxRepository inboxRepository;
    private final int fanoutThreshold;

    public InboxService(ImConversationRepository conversationRepository,
                        ImConversationMemberRepository conversationMemberRepository,
                        ImInboxRepository inboxRepository,
                        @Value("${im.inbox.fanout-threshold:1000}") int fanoutThreshold) {
        this.conversationRepository = conversationRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.inboxRepository = inboxRepository;
        this.fanoutThreshold = Math.max(0, fanoutThreshold);
    }

    @Transactional
    public void fanOutMessage(Long conversationId, Long messageId, Long messageSeq) {
        ImConversationPo conversation = conversationRepository.findByIdAndDeletedFalse(conversationId)
                .orElseThrow(() -> new ImException("IM_CONVERSATION_NOT_FOUND"));
        List<ImConversationMemberPo> activeMembers = conversationMemberRepository
                .findByConversationIdAndDeletedFalse(conversationId)
                .stream()
                .filter(member -> ImMembershipStatus.ACTIVE.equals(member.getStatus()))
                .toList();
        boolean dmConversation = ImConversationType.DM.equals(conversation.getConversationTypeEnum());
        if (!dmConversation && activeMembers.size() > fanoutThreshold) {
            return;
        }
        List<ImInboxPo> inboxes = activeMembers.stream()
                .map(member -> inbox(member.getUserId(), conversationId, messageId, messageSeq))
                .toList();
        if (!inboxes.isEmpty()) {
            inboxRepository.saveAllAndFlush(inboxes);
        }
    }

    @Transactional
    public int markReadUpTo(Long userId, Long conversationId, Long messageSeq) {
        return inboxRepository.markReadUpTo(userId, conversationId, messageSeq);
    }

    private ImInboxPo inbox(Long userId, Long conversationId, Long messageId, Long messageSeq) {
        ImInboxPo inbox = new ImInboxPo();
        inbox.setUserId(userId);
        inbox.setConversationId(conversationId);
        inbox.setMessageId(messageId);
        inbox.setMessageSeq(messageSeq);
        inbox.setRead(false);
        inbox.setMetadataJson("{}");
        return inbox;
    }
}
