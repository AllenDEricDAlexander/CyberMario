package top.egon.mario.im;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.egon.mario.im.facade.DmFacade;
import top.egon.mario.im.facade.ImFacade;
import top.egon.mario.im.facade.RoomFacade;
import top.egon.mario.im.facade.dto.command.CreateGroupCommand;
import top.egon.mario.im.facade.dto.command.JoinCommand;
import top.egon.mario.im.facade.dto.command.MarkReadCommand;
import top.egon.mario.im.facade.dto.command.OpenDmCommand;
import top.egon.mario.im.facade.dto.command.SendMessageCommand;
import top.egon.mario.im.facade.dto.query.ListConversationsQuery;
import top.egon.mario.im.facade.dto.view.ConversationView;
import top.egon.mario.im.facade.dto.view.GroupView;
import top.egon.mario.im.facade.dto.view.MessageView;
import top.egon.mario.im.facade.dto.view.UnreadView;
import top.egon.mario.im.po.ImInboxPo;
import top.egon.mario.im.po.enums.ImOutboxEventType;
import top.egon.mario.im.po.enums.ImOutboxStatus;
import top.egon.mario.im.policy.ImPrincipal;
import top.egon.mario.im.repository.ImInboxRepository;
import top.egon.mario.im.repository.ImOutboxRepository;
import top.egon.mario.im.service.ConversationService;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.ai.dashscope.api-key=test-api-key",
        "im.inbox.fanout-threshold=1"
})
class ImInboxServiceTests {

    private static final String CONTEXT_TYPE = "IM_INBOX_SERVICE_TEST";

    @Autowired
    private DmFacade dmFacade;

    @Autowired
    private ImFacade imFacade;

    @Autowired
    private RoomFacade roomFacade;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private ImInboxRepository inboxRepository;

    @Autowired
    private ImOutboxRepository outboxRepository;

    @Test
    void dmSendCreatesPendingOutboxAndInboxRowsForBothActiveMembersThenMarkReadUpdatesReceiverInbox() {
        ConversationView dm = dmFacade.openDm(new OpenDmCommand(principal(9101L), 9102L));

        MessageView message = imFacade.send(send(9101L, dm.id(), "dm-inbox-1"));

        assertThat(outboxes(dm.id()))
                .hasSize(1)
                .singleElement()
                .satisfies(outbox -> {
                    assertThat(outbox.getMessageId()).isEqualTo(message.id());
                    assertThat(outbox.getMessageSeq()).isEqualTo(1L);
                    assertThat(outbox.getEventType()).isEqualTo(ImOutboxEventType.MESSAGE_CREATED);
                    assertThat(outbox.getStatus()).isEqualTo(ImOutboxStatus.PENDING);
                });
        assertThat(inboxes(dm.id()))
                .hasSize(2)
                .allSatisfy(inbox -> {
                    assertThat(inbox.getMessageId()).isEqualTo(message.id());
                    assertThat(inbox.getMessageSeq()).isEqualTo(1L);
                    assertThat(inbox.getRead()).isFalse();
                })
                .extracting(ImInboxPo::getUserId)
                .containsExactlyInAnyOrder(9101L, 9102L);

        UnreadView unread = imFacade.markRead(new MarkReadCommand(principal(9102L), dm.id(), 1L));

        assertThat(unread.lastReadSeq()).isEqualTo(1L);
        assertThat(unread.unreadCount()).isZero();
        assertThat(inboxes(dm.id()).stream()
                .filter(inbox -> Long.valueOf(9102L).equals(inbox.getUserId()))
                .toList())
                .hasSize(1)
                .allSatisfy(inbox -> assertThat(inbox.getRead()).isTrue());
    }

    @Test
    void cursorDeliveryGroupSkipsInboxFanoutButUnreadStillComesFromSequenceCursor() {
        GroupView group = conversationService.createGroup(new CreateGroupCommand(
                principal(9201L), null, CONTEXT_TYPE, null, "cursor-group", "Cursor Group", "OPEN", "{}"));
        LongStream.rangeClosed(9202L, 9221L).forEach(userId -> joinGroup(userId, group));

        imFacade.send(send(9201L, group.conversationId(), "cursor-group-1"));

        assertThat(inboxes(group.conversationId())).isEmpty();
        assertThat(imFacade.listConversations(new ListConversationsQuery(principal(9202L), CONTEXT_TYPE, null)))
                .filteredOn(conversation -> group.conversationId().equals(conversation.id()))
                .singleElement()
                .extracting(ConversationView::unreadCount)
                .isEqualTo(1L);
    }

    @Test
    void nonDmConversationAtFanoutThresholdCreatesInboxRows() {
        GroupView group = conversationService.createGroup(new CreateGroupCommand(
                principal(9231L), null, CONTEXT_TYPE, null, "threshold-group", "Threshold Group", "OPEN", "{}"));

        MessageView message = imFacade.send(send(9231L, group.conversationId(), "threshold-group-1"));

        assertThat(inboxes(group.conversationId()))
                .hasSize(1)
                .singleElement()
                .satisfies(inbox -> {
                    assertThat(inbox.getUserId()).isEqualTo(9231L);
                    assertThat(inbox.getMessageId()).isEqualTo(message.id());
                    assertThat(inbox.getRead()).isFalse();
                });
    }

    private void joinGroup(Long userId, GroupView group) {
        roomFacade.applyJoin(new JoinCommand(principal(userId), "GROUP", group.id(), "join"));
    }

    private SendMessageCommand send(Long userId, Long conversationId, String clientMsgId) {
        return new SendMessageCommand(principal(userId), conversationId, clientMsgId, "TEXT", clientMsgId, "{}", "{}");
    }

    private List<top.egon.mario.im.po.ImOutboxPo> outboxes(Long conversationId) {
        return outboxRepository.findAll().stream()
                .filter(outbox -> conversationId.equals(outbox.getConversationId()))
                .toList();
    }

    private List<ImInboxPo> inboxes(Long conversationId) {
        return inboxRepository.findAll().stream()
                .filter(inbox -> conversationId.equals(inbox.getConversationId()))
                .toList();
    }

    private ImPrincipal principal(Long userId) {
        return new ImPrincipal(userId, Set.of(), CONTEXT_TYPE, Map.of());
    }
}
