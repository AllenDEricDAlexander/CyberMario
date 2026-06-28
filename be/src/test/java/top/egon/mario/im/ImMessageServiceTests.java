package top.egon.mario.im;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import top.egon.mario.im.facade.ImFacade;
import top.egon.mario.im.facade.RoomFacade;
import top.egon.mario.im.facade.dto.command.CreateChannelCommand;
import top.egon.mario.im.facade.dto.command.CreateGroupCommand;
import top.egon.mario.im.facade.dto.command.JoinCommand;
import top.egon.mario.im.facade.dto.command.MarkReadCommand;
import top.egon.mario.im.facade.dto.command.SendMessageCommand;
import top.egon.mario.im.facade.dto.query.AuditHistoryQuery;
import top.egon.mario.im.facade.dto.query.HistoryQuery;
import top.egon.mario.im.facade.dto.view.ChannelView;
import top.egon.mario.im.facade.dto.view.GroupView;
import top.egon.mario.im.facade.dto.view.MessageView;
import top.egon.mario.im.facade.dto.view.UnreadView;
import top.egon.mario.im.po.ImInboxPo;
import top.egon.mario.im.po.ImOutboxPo;
import top.egon.mario.im.po.enums.ImOutboxEventType;
import top.egon.mario.im.policy.ImPrincipal;
import top.egon.mario.im.repository.ImConversationMemberRepository;
import top.egon.mario.im.repository.ImConversationRepository;
import top.egon.mario.im.repository.ImInboxRepository;
import top.egon.mario.im.repository.ImMessageRepository;
import top.egon.mario.im.repository.ImOutboxRepository;
import top.egon.mario.im.service.ConversationService;
import top.egon.mario.im.facade.ImException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class ImMessageServiceTests {

    private static final String CONTEXT_TYPE = "IM_MESSAGE_SERVICE_TEST";

    @Autowired
    private ImFacade imFacade;

    @Autowired
    private RoomFacade roomFacade;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private ImConversationRepository conversationRepository;

    @Autowired
    private ImConversationMemberRepository conversationMemberRepository;

    @Autowired
    private ImMessageRepository messageRepository;

    @Autowired
    private ImOutboxRepository outboxRepository;

    @Autowired
    private ImInboxRepository inboxRepository;

    @Test
    void sendWithClientMessageIdUpdatesConversationOutboxInboxAndIsIdempotent() {
        ChannelView channel = channel(5001L, "send-idempotent");
        join(5002L, channel);

        MessageView first = imFacade.send(send(5001L, channel.mainConversationId(), "client-one", "hello"));
        MessageView duplicate = imFacade.send(send(5001L, channel.mainConversationId(), "client-one", "ignored"));

        assertThat(first.messageSeq()).isEqualTo(1L);
        assertThat(duplicate.id()).isEqualTo(first.id());
        assertThat(duplicate.messageSeq()).isEqualTo(1L);
        assertThat(duplicate.content()).isEqualTo("hello");
        assertThat(conversationRepository.findByIdAndDeletedFalse(channel.mainConversationId())).get()
                .satisfies(conversation -> {
                    assertThat(conversation.getMessageSeq()).isEqualTo(1L);
                    assertThat(conversation.getLastMessageId()).isEqualTo(first.id());
                    assertThat(conversation.getLastMessageAt()).isEqualTo(first.sentAt());
                    assertThat(conversation.getLastActiveAt()).isEqualTo(first.sentAt());
                });
        assertThat(messages(channel.mainConversationId())).hasSize(1);
        assertThat(outboxes(channel.mainConversationId()))
                .hasSize(1)
                .first()
                .satisfies(outbox -> {
                    assertThat(outbox.getMessageId()).isEqualTo(first.id());
                    assertThat(outbox.getMessageSeq()).isEqualTo(1L);
                    assertThat(outbox.getEventType()).isEqualTo(ImOutboxEventType.MESSAGE_CREATED);
                });
        assertThat(inboxes(channel.mainConversationId()))
                .hasSize(2)
                .extracting(ImInboxPo::getUserId)
                .containsExactlyInAnyOrder(5001L, 5002L);
    }

    @Test
    void sendRejectsClientMessageIdLongerThanSchemaLimit() {
        ChannelView channel = channel(5051L, "client-msg-too-long");

        assertThatThrownBy(() -> imFacade.send(send(
                5051L, channel.mainConversationId(), "x".repeat(129), "too-long")))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_CLIENT_MSG_ID_TOO_LONG");
        assertThat(messageRepository.countByConversationIdAndDeletedFalse(channel.mainConversationId())).isZero();
    }

    @Test
    void consecutiveSendsAllocateGapFreeSequences() {
        ChannelView channel = channel(5101L, "gap-free");

        List<Long> sequences = List.of(
                imFacade.send(send(5101L, channel.mainConversationId(), "gap-1", "one")).messageSeq(),
                imFacade.send(send(5101L, channel.mainConversationId(), "gap-2", "two")).messageSeq(),
                imFacade.send(send(5101L, channel.mainConversationId(), "gap-3", "three")).messageSeq());

        assertThat(sequences).containsExactly(1L, 2L, 3L);
        assertThat(messages(channel.mainConversationId()))
                .extracting(MessageView::messageSeq)
                .containsExactly(1L, 2L, 3L);
    }

    @Test
    void concurrentSendsAgainstSameConversationAllocateUniqueGapFreeSequences() throws Exception {
        ChannelView channel = channel(5201L, "concurrent-gap-free");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            Future<MessageView> first = executorService.submit(concurrentSend(
                    ready, start, 5201L, channel.mainConversationId(), "concurrent-1"));
            Future<MessageView> second = executorService.submit(concurrentSend(
                    ready, start, 5201L, channel.mainConversationId(), "concurrent-2"));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))
                    .stream()
                    .map(MessageView::messageSeq)
                    .sorted()
                    .toList())
                    .containsExactly(1L, 2L);
            assertThat(messages(channel.mainConversationId()))
                    .extracting(MessageView::messageSeq)
                    .containsExactly(1L, 2L);
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void nonMemberSendToChannelMainIsDenied() {
        ChannelView channel = channel(5301L, "non-member-send");

        assertThatThrownBy(() -> imFacade.send(send(5302L, channel.mainConversationId(), "denied", "nope")))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_SEND_DENIED");
        assertThat(messageRepository.countByConversationIdAndDeletedFalse(channel.mainConversationId())).isZero();
    }

    @Test
    void channelMainHistoryIsPublicButGroupHistoryRequiresMembership() {
        ChannelView channel = channel(5401L, "public-history");
        MessageView channelMessage = imFacade.send(send(
                5401L, channel.mainConversationId(), "public-history-1", "visible"));
        GroupView group = group(5401L, "private-group");
        imFacade.send(send(5401L, group.conversationId(), "group-history-1", "private"));

        Page<MessageView> publicHistory = imFacade.history(history(5402L, channel.mainConversationId()));

        assertThat(publicHistory.getContent())
                .extracting(MessageView::id)
                .containsExactly(channelMessage.id());
        assertThatThrownBy(() -> imFacade.history(history(5402L, group.conversationId())))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_HISTORY_FORBIDDEN");
    }

    @Test
    void auditHistoryRejectsDirectCallerWithoutAuditRole() {
        ChannelView channel = channel(5451L, "audit-no-role");
        imFacade.send(send(5451L, channel.mainConversationId(), "audit-no-role-1", "hidden"));

        assertThatThrownBy(() -> imFacade.auditHistory(new AuditHistoryQuery(
                principal(5452L), channel.mainConversationId(), 0, 20, null, null)))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_AUDIT_FORBIDDEN");
    }

    @Test
    void auditHistoryCapsOversizedPageRequestsAtAuditLimit() {
        ChannelView channel = channel(5461L, "audit-page-cap");
        for (int index = 1; index <= 201; index++) {
            imFacade.send(send(5461L, channel.mainConversationId(), "audit-cap-" + index, "message-" + index));
        }

        Page<MessageView> page = imFacade.auditHistory(new AuditHistoryQuery(
                auditPrincipal(5462L), channel.mainConversationId(), 0, 500, null, null));

        assertThat(page.getSize()).isEqualTo(200);
        assertThat(page.getContent()).hasSize(200);
    }

    @Test
    void markReadOnlyAdvancesCursorAndMarksInboxRowsReadUpToTarget() {
        ChannelView channel = channel(5501L, "mark-read");
        join(5502L, channel);
        imFacade.send(send(5501L, channel.mainConversationId(), "mark-1", "one"));
        imFacade.send(send(5501L, channel.mainConversationId(), "mark-2", "two"));
        imFacade.send(send(5501L, channel.mainConversationId(), "mark-3", "three"));

        UnreadView atThree = imFacade.markRead(new MarkReadCommand(
                principal(5502L), channel.mainConversationId(), 3L));
        UnreadView atOne = imFacade.markRead(new MarkReadCommand(
                principal(5502L), channel.mainConversationId(), 1L));

        assertThat(atThree.lastReadSeq()).isEqualTo(3L);
        assertThat(atThree.unreadCount()).isZero();
        assertThat(atOne.lastReadSeq()).isEqualTo(3L);
        assertThat(atOne.unreadCount()).isZero();
        assertThat(conversationMemberRepository.findActiveByConversationIdAndUserId(
                channel.mainConversationId(), 5502L)).get()
                .extracting(member -> member.getLastReadSeq())
                .isEqualTo(3L);
        assertThat(inboxes(channel.mainConversationId()).stream()
                .filter(inbox -> Long.valueOf(5502L).equals(inbox.getUserId()))
                .toList())
                .hasSize(3)
                .allSatisfy(inbox -> assertThat(inbox.getRead()).isTrue());
        assertThat(outboxes(channel.mainConversationId()))
                .filteredOn(outbox -> ImOutboxEventType.READ_UPDATED.equals(outbox.getEventType()))
                .hasSize(1);
    }

    @Test
    void markReadOutboxReferencesReadCursorMessageInsteadOfLatestMessage() {
        ChannelView channel = channel(5551L, "mark-read-cursor-message");
        join(5552L, channel);
        MessageView first = imFacade.send(send(5551L, channel.mainConversationId(), "cursor-1", "one"));
        imFacade.send(send(5551L, channel.mainConversationId(), "cursor-2", "two"));
        MessageView latest = imFacade.send(send(5551L, channel.mainConversationId(), "cursor-3", "three"));

        UnreadView unread = imFacade.markRead(new MarkReadCommand(
                principal(5552L), channel.mainConversationId(), 1L));

        assertThat(unread.lastReadSeq()).isEqualTo(1L);
        assertThat(unread.unreadCount()).isEqualTo(2L);
        assertThat(outboxes(channel.mainConversationId()))
                .filteredOn(outbox -> ImOutboxEventType.READ_UPDATED.equals(outbox.getEventType()))
                .singleElement()
                .satisfies(outbox -> {
                    assertThat(outbox.getMessageId()).isEqualTo(first.id());
                    assertThat(outbox.getMessageId()).isNotEqualTo(latest.id());
                    assertThat(outbox.getMessageSeq()).isEqualTo(1L);
                });
    }

    @Test
    void markReadToZeroInEmptyConversationDoesNotWriteReadUpdatedOutbox() {
        ChannelView channel = channel(5561L, "empty-mark-read-zero");

        UnreadView unread = imFacade.markRead(new MarkReadCommand(
                principal(5561L), channel.mainConversationId(), 0L));

        assertThat(unread.lastReadSeq()).isZero();
        assertThat(unread.unreadCount()).isZero();
        assertThat(outboxes(channel.mainConversationId()))
                .filteredOn(outbox -> ImOutboxEventType.READ_UPDATED.equals(outbox.getEventType()))
                .isEmpty();
    }

    private Callable<MessageView> concurrentSend(CountDownLatch ready, CountDownLatch start, Long userId,
                                                 Long conversationId, String clientMsgId) {
        return () -> {
            ready.countDown();
            assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
            return imFacade.send(send(userId, conversationId, clientMsgId, clientMsgId));
        };
    }

    private ChannelView channel(Long ownerUserId, String key) {
        return conversationService.createChannel(new CreateChannelCommand(
                principal(ownerUserId), CONTEXT_TYPE, null, key, key, "OPEN", "{}"));
    }

    private GroupView group(Long ownerUserId, String key) {
        return conversationService.createGroup(new CreateGroupCommand(
                principal(ownerUserId), null, CONTEXT_TYPE, null, key, key, "OPEN", "{}"));
    }

    private void join(Long userId, ChannelView channel) {
        roomFacade.applyJoin(new JoinCommand(principal(userId), "CHANNEL", channel.id(), "join"));
    }

    private SendMessageCommand send(Long userId, Long conversationId, String clientMsgId, String content) {
        return new SendMessageCommand(principal(userId), conversationId, clientMsgId, "TEXT", content, "{}", "{}");
    }

    private HistoryQuery history(Long userId, Long conversationId) {
        return new HistoryQuery(principal(userId), conversationId, 0, 20, null, null);
    }

    private List<MessageView> messages(Long conversationId) {
        return messageRepository.findByConversationIdAndDeletedFalseOrderByMessageSeqAsc(
                        conversationId, org.springframework.data.domain.PageRequest.of(0, 20))
                .getContent()
                .stream()
                .map(message -> new MessageView(
                        message.getId(),
                        message.getConversationId(),
                        message.getSenderUserId(),
                        message.getMessageSeq(),
                        message.getClientMsgId(),
                        message.getMessageType(),
                        message.getContent(),
                        message.getPayloadJson(),
                        message.getStatus().name(),
                        message.getSentAt(),
                        message.getEditedAt(),
                        message.getDeletedAt(),
                        message.getMetadataJson()))
                .toList();
    }

    private List<ImOutboxPo> outboxes(Long conversationId) {
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

    private ImPrincipal auditPrincipal(Long userId) {
        return new ImPrincipal(userId, Set.of("SUPER_ADMIN"), CONTEXT_TYPE, Map.of());
    }
}
