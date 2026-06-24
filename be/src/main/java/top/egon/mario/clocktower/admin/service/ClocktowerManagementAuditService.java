package top.egon.mario.clocktower.admin.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import top.egon.mario.clocktower.admin.dto.ClocktowerGameAuditResponse;
import top.egon.mario.clocktower.admin.dto.ClocktowerRoomAuditResponse;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatMessageResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

public interface ClocktowerManagementAuditService {

    ClocktowerRoomAuditResponse auditRoom(Long roomId, RbacPrincipal principal);

    ClocktowerGameAuditResponse auditGame(Long gameId, RbacPrincipal principal);

    Page<ClocktowerChatMessageResponse> messages(Long conversationId, Pageable pageable, RbacPrincipal principal);
}
