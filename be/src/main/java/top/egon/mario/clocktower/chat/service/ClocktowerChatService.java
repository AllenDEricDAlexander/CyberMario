package top.egon.mario.clocktower.chat.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatConversationResponse;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatMarkReadRequest;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatMessageResponse;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatPrivateConversationRequest;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatReadStateResponse;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatSendMessageRequest;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

public interface ClocktowerChatService {

    List<ClocktowerChatConversationResponse> conversations(Long roomId, RbacPrincipal principal);

    List<ClocktowerChatConversationResponse> conversationsForGame(Long roomId, Long gameId, RbacPrincipal principal);

    List<ClocktowerChatConversationResponse> auditConversations(Long roomId, RbacPrincipal principal);

    Page<ClocktowerChatMessageResponse> messages(Long conversationId, Pageable pageable, RbacPrincipal principal);

    ClocktowerChatConversationResponse privateConversation(ClocktowerChatPrivateConversationRequest request,
                                                           RbacPrincipal principal);

    ClocktowerChatMessageResponse sendMessage(Long conversationId, ClocktowerChatSendMessageRequest request,
                                              RbacPrincipal principal);

    ClocktowerChatReadStateResponse markRead(Long conversationId, ClocktowerChatMarkReadRequest request,
                                             RbacPrincipal principal);
}
