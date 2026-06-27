package top.egon.mario.im.realtime;

import java.util.Map;

public interface RealtimeRouter {

    void deliverToUser(Long userId, Map<String, Object> frame);

    void deliverToConversation(Long conversationId, Map<String, Object> frame);
}
