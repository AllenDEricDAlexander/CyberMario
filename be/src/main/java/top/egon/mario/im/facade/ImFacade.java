package top.egon.mario.im.facade;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import top.egon.mario.im.facade.dto.command.MarkReadCommand;
import top.egon.mario.im.facade.dto.command.MintWsTicketCommand;
import top.egon.mario.im.facade.dto.command.SendMessageCommand;
import top.egon.mario.im.facade.dto.query.HistoryQuery;
import top.egon.mario.im.facade.dto.query.ListConversationsQuery;
import top.egon.mario.im.facade.dto.view.ConversationView;
import top.egon.mario.im.facade.dto.view.MessageView;
import top.egon.mario.im.facade.dto.view.UnreadView;
import top.egon.mario.im.facade.dto.view.WsTicketView;
import top.egon.mario.im.service.ImException;

import java.util.List;

@Component
public class ImFacade {

    public MessageView send(SendMessageCommand command) {
        throw notImplemented();
    }

    public Page<MessageView> history(HistoryQuery query) {
        throw notImplemented();
    }

    public UnreadView markRead(MarkReadCommand command) {
        throw notImplemented();
    }

    public List<ConversationView> listConversations(ListConversationsQuery query) {
        throw notImplemented();
    }

    public WsTicketView mintWsTicket(MintWsTicketCommand command) {
        throw notImplemented();
    }

    private static ImException notImplemented() {
        return new ImException("IM_FACADE_NOT_IMPLEMENTED",
                "IM facade contract is defined; business implementation is pending");
    }
}
