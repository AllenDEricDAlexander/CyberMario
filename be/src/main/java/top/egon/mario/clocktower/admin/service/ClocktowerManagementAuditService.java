package top.egon.mario.clocktower.admin.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import top.egon.mario.clocktower.admin.dto.ClocktowerAuditQuery;
import top.egon.mario.clocktower.admin.dto.ClocktowerAuditReportResponse;
import top.egon.mario.clocktower.admin.dto.ClocktowerAuditSummaryResponse;
import top.egon.mario.clocktower.admin.dto.ClocktowerGameAuditResponse;
import top.egon.mario.clocktower.admin.dto.ClocktowerRoomAuditResponse;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatMessageResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

public interface ClocktowerManagementAuditService {

    ClocktowerAuditSummaryResponse summary(ClocktowerAuditQuery query, RbacPrincipal principal);

    Page<ClocktowerAuditReportResponse.Room> rooms(ClocktowerAuditQuery query, Pageable pageable,
                                                   RbacPrincipal principal);

    Page<ClocktowerAuditReportResponse.Game> games(ClocktowerAuditQuery query, Pageable pageable,
                                                   RbacPrincipal principal);

    Page<ClocktowerAuditReportResponse.Event> events(ClocktowerAuditQuery query, Pageable pageable,
                                                     RbacPrincipal principal);

    Page<ClocktowerAuditReportResponse.Conversation> conversations(ClocktowerAuditQuery query, Pageable pageable,
                                                                   RbacPrincipal principal);

    Page<ClocktowerAuditReportResponse.Message> messages(ClocktowerAuditQuery query, Pageable pageable,
                                                         RbacPrincipal principal);

    Page<ClocktowerAuditReportResponse.Member> members(ClocktowerAuditQuery query, Pageable pageable,
                                                       RbacPrincipal principal);

    Page<ClocktowerAuditReportResponse.Invitation> invitations(ClocktowerAuditQuery query, Pageable pageable,
                                                               RbacPrincipal principal);

    Page<ClocktowerAuditReportResponse.Ban> bans(ClocktowerAuditQuery query, Pageable pageable,
                                                 RbacPrincipal principal);

    ClocktowerRoomAuditResponse auditRoom(Long roomId, RbacPrincipal principal);

    ClocktowerGameAuditResponse auditGame(Long gameId, RbacPrincipal principal);

    Page<ClocktowerChatMessageResponse> messages(Long conversationId, Pageable pageable, RbacPrincipal principal);
}
