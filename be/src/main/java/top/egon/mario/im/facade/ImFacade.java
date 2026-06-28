package top.egon.mario.im.facade;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import top.egon.mario.im.facade.dto.command.MarkReadCommand;
import top.egon.mario.im.facade.dto.command.MintWsTicketCommand;
import top.egon.mario.im.facade.dto.command.SendMessageCommand;
import top.egon.mario.im.facade.dto.query.AuditHistoryQuery;
import top.egon.mario.im.facade.dto.query.ConversationMemberQuery;
import top.egon.mario.im.facade.dto.query.ConversationSurfaceQuery;
import top.egon.mario.im.facade.dto.query.HistoryQuery;
import top.egon.mario.im.facade.dto.query.ListConversationsQuery;
import top.egon.mario.im.facade.dto.view.ConversationView;
import top.egon.mario.im.facade.dto.view.ConversationSurfaceView;
import top.egon.mario.im.facade.dto.view.MessageView;
import top.egon.mario.im.facade.dto.view.UnreadView;
import top.egon.mario.im.facade.dto.view.WsTicketView;
import top.egon.mario.im.service.ConversationService;
import top.egon.mario.im.service.ImTicketService;
import top.egon.mario.im.service.MessageService;

import java.util.List;
import java.util.Optional;

@Component
public class ImFacade {

    private final MessageService messageService;
    private final ConversationService conversationService;
    private final ImTicketService ticketService;

    public ImFacade(MessageService messageService, ConversationService conversationService,
                    ImTicketService ticketService) {
        this.messageService = messageService;
        this.conversationService = conversationService;
        this.ticketService = ticketService;
    }

    public MessageView send(SendMessageCommand command) {
        return messageService.send(command);
    }

    public Page<MessageView> history(HistoryQuery query) {
        return messageService.history(query);
    }

    public Page<MessageView> auditHistory(AuditHistoryQuery query) {
        return messageService.auditHistory(query);
    }

    public UnreadView markRead(MarkReadCommand command) {
        return messageService.markRead(command);
    }

    public List<ConversationView> listConversations(ListConversationsQuery query) {
        return conversationService.listConversations(query);
    }

    public Optional<ConversationSurfaceView> findConversationSurface(ConversationSurfaceQuery query) {
        return conversationService.findConversationSurface(query);
    }

    public boolean hasActiveConversationMember(ConversationMemberQuery query) {
        return conversationService.hasActiveConversationMember(query);
    }

    public WsTicketView mintWsTicket(MintWsTicketCommand command) {
        return ticketService.mint(command);
    }
}
