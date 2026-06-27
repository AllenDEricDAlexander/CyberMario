package top.egon.mario.im.realtime;

import org.springframework.stereotype.Service;
import top.egon.mario.im.po.enums.ImMembershipStatus;
import top.egon.mario.im.repository.ImConversationMemberRepository;

import java.util.Map;

@Service
public class LocalRealtimeRouter implements RealtimeRouter {

    private final ImConnectionRegistry connectionRegistry;
    private final ImConversationMemberRepository conversationMemberRepository;

    public LocalRealtimeRouter(ImConnectionRegistry connectionRegistry,
                               ImConversationMemberRepository conversationMemberRepository) {
        this.connectionRegistry = connectionRegistry;
        this.conversationMemberRepository = conversationMemberRepository;
    }

    @Override
    public void deliverToUser(Long userId, Map<String, Object> frame) {
        connectionRegistry.deliverToUser(userId, frame);
    }

    @Override
    public void deliverToConversation(Long conversationId, Map<String, Object> frame) {
        conversationMemberRepository.findByConversationIdAndDeletedFalse(conversationId)
                .stream()
                .filter(member -> ImMembershipStatus.ACTIVE.equals(member.getStatus()))
                .map(member -> member.getUserId())
                .distinct()
                .forEach(userId -> deliverToUser(userId, frame));
    }
}
