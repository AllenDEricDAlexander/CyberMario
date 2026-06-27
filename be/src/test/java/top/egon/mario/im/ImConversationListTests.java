package top.egon.mario.im;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.egon.mario.im.facade.ImFacade;
import top.egon.mario.im.facade.RoomFacade;
import top.egon.mario.im.facade.dto.command.CreateChannelCommand;
import top.egon.mario.im.facade.dto.command.JoinCommand;
import top.egon.mario.im.facade.dto.command.MarkReadCommand;
import top.egon.mario.im.facade.dto.command.SendMessageCommand;
import top.egon.mario.im.facade.dto.query.ListConversationsQuery;
import top.egon.mario.im.facade.dto.view.ChannelView;
import top.egon.mario.im.facade.dto.view.ConversationView;
import top.egon.mario.im.facade.dto.view.MessageView;
import top.egon.mario.im.policy.ImPrincipal;
import top.egon.mario.im.service.ConversationService;

import java.lang.reflect.RecordComponent;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class ImConversationListTests {

    private static final String CONTEXT_TYPE = "IM_CONVERSATION_LIST_TEST";

    @Autowired
    private ImFacade imFacade;

    @Autowired
    private RoomFacade roomFacade;

    @Autowired
    private ConversationService conversationService;

    @Test
    void conversationViewExposesUnreadCountForListResults() {
        assertThat(List.of(ConversationView.class.getRecordComponents())
                .stream()
                .map(RecordComponent::getName))
                .contains("unreadCount", "lastMessage");
    }

    @Test
    void listConversationsReturnsActiveMembershipsSortedByActivityWithUnreadCountsAndLastMessage() {
        ChannelView empty = channel(7991L, "empty-list-conversation");
        join(8002L, empty);

        ChannelView older = channel(8001L, "older-list-conversation");
        join(8002L, older);
        imFacade.send(send(8001L, older.mainConversationId(), "older-list-1"));
        MessageView olderLatest = imFacade.send(send(8001L, older.mainConversationId(), "older-list-2"));
        imFacade.markRead(new MarkReadCommand(principal(8002L), older.mainConversationId(), 1L));

        ChannelView newer = channel(8003L, "newer-list-conversation");
        join(8002L, newer);
        imFacade.send(send(8003L, newer.mainConversationId(), "newer-list-1"));
        imFacade.send(send(8003L, newer.mainConversationId(), "newer-list-2"));
        MessageView newerLatest = imFacade.send(send(8003L, newer.mainConversationId(), "newer-list-3"));

        ChannelView notJoined = channel(8004L, "not-joined-public-channel");
        imFacade.send(send(8004L, notJoined.mainConversationId(), "not-joined-list-1"));

        List<ConversationView> conversations = imFacade.listConversations(
                new ListConversationsQuery(principal(8002L), CONTEXT_TYPE, null));

        assertThat(conversations)
                .extracting(ConversationView::id)
                .containsExactly(newer.mainConversationId(), older.mainConversationId(), empty.mainConversationId());
        assertThat(unreadCount(conversations.get(0))).isEqualTo(3L);
        assertThat(unreadCount(conversations.get(1))).isEqualTo(1L);
        assertThat(unreadCount(conversations.get(2))).isZero();
        assertThat(lastMessage(conversations.get(0))).satisfies(lastMessage -> {
            assertThat(lastMessage.id()).isEqualTo(newerLatest.id());
            assertThat(lastMessage.messageSeq()).isEqualTo(newerLatest.messageSeq());
            assertThat(lastMessage.content()).isEqualTo(newerLatest.content());
        });
        assertThat(lastMessage(conversations.get(1))).satisfies(lastMessage -> {
            assertThat(lastMessage.id()).isEqualTo(olderLatest.id());
            assertThat(lastMessage.messageSeq()).isEqualTo(olderLatest.messageSeq());
            assertThat(lastMessage.content()).isEqualTo(olderLatest.content());
        });
        assertThat(lastMessage(conversations.get(2))).isNull();
        assertThat(conversations)
                .extracting(ConversationView::id)
                .doesNotContain(notJoined.mainConversationId());
    }

    private ChannelView channel(Long ownerUserId, String key) {
        return conversationService.createChannel(new CreateChannelCommand(
                principal(ownerUserId), CONTEXT_TYPE, null, key, key, "OPEN", "{}"));
    }

    private void join(Long userId, ChannelView channel) {
        roomFacade.applyJoin(new JoinCommand(principal(userId), "CHANNEL", channel.id(), "join"));
    }

    private SendMessageCommand send(Long userId, Long conversationId, String clientMsgId) {
        return new SendMessageCommand(principal(userId), conversationId, clientMsgId, "TEXT", clientMsgId, "{}", "{}");
    }

    private Long unreadCount(ConversationView view) {
        try {
            return (Long) ConversationView.class.getMethod("unreadCount").invoke(view);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("ConversationView must expose unreadCount", ex);
        }
    }

    private MessageView lastMessage(ConversationView view) {
        try {
            return (MessageView) ConversationView.class.getMethod("lastMessage").invoke(view);
        } catch (InvocationTargetException ex) {
            throw new AssertionError("ConversationView.lastMessage failed", ex.getCause());
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("ConversationView must expose lastMessage", ex);
        }
    }

    private ImPrincipal principal(Long userId) {
        return new ImPrincipal(userId, Set.of(), CONTEXT_TYPE, Map.of());
    }
}
