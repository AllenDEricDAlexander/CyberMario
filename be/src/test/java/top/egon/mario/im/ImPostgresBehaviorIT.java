package top.egon.mario.im;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import top.egon.mario.im.facade.DmFacade;
import top.egon.mario.im.facade.GovFacade;
import top.egon.mario.im.service.ImException;
import top.egon.mario.im.facade.ImFacade;
import top.egon.mario.im.facade.RoomFacade;
import top.egon.mario.im.facade.dto.command.ApproveCommand;
import top.egon.mario.im.facade.dto.command.BanUserCommand;
import top.egon.mario.im.facade.dto.command.BlockUserCommand;
import top.egon.mario.im.facade.dto.command.CreateChannelCommand;
import top.egon.mario.im.facade.dto.command.CreateGroupCommand;
import top.egon.mario.im.facade.dto.command.GlobalMuteCommand;
import top.egon.mario.im.facade.dto.command.JoinCommand;
import top.egon.mario.im.facade.dto.command.MarkReadCommand;
import top.egon.mario.im.facade.dto.command.MuteUserCommand;
import top.egon.mario.im.facade.dto.command.OpenDmCommand;
import top.egon.mario.im.facade.dto.command.SendMessageCommand;
import top.egon.mario.im.facade.dto.query.HistoryQuery;
import top.egon.mario.im.facade.dto.query.ListConversationsQuery;
import top.egon.mario.im.facade.dto.view.ChannelView;
import top.egon.mario.im.facade.dto.view.ConversationView;
import top.egon.mario.im.facade.dto.view.GroupView;
import top.egon.mario.im.facade.dto.view.JoinResultView;
import top.egon.mario.im.facade.dto.view.MessageView;
import top.egon.mario.im.facade.dto.view.UnreadView;
import top.egon.mario.im.policy.ImPrincipal;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ContextConfiguration(initializers = ImPostgresBehaviorIT.PostgresTestProperties.class)
class ImPostgresBehaviorIT {

    private static final String ENV_URL = "IM_POSTGRES_TEST_URL";
    private static final String ENV_USERNAME = "IM_POSTGRES_TEST_USERNAME";
    private static final String ENV_PASSWORD = "IM_POSTGRES_TEST_PASSWORD";
    private static final String SCHEMA_NAME = newSchemaName("im_behavior");
    private static final String CONTEXT_PREFIX = "IM_POSTGRES_BEHAVIOR_IT";
    private static final AtomicLong UNIQUE = new AtomicLong(System.currentTimeMillis());

    private static final List<String> DATA_CLEANUP_ORDER = List.of(
            "im_inbox",
            "im_outbox",
            "im_message",
            "im_conversation_member",
            "im_dm_pair",
            "im_join_request",
            "im_membership",
            "im_global_mute",
            "im_dm_block",
            "im_ban",
            "im_ws_ticket",
            "im_group",
            "im_channel",
            "im_conversation"
    );

    @Autowired
    private RoomFacade roomFacade;

    @Autowired
    private ImFacade imFacade;

    @Autowired
    private DmFacade dmFacade;

    @Autowired
    private GovFacade govFacade;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        assertUsingIsolatedSchema();
        deleteOnlyImRows();
    }

    @Test
    void sendWithClientMessageIdIsIdempotentOnPostgreSql() {
        TestScope scope = scope("idem");
        Long sender = scope.user(1);
        Long recipient = scope.user(2);
        ChannelView channel = channel(scope, sender, "channel", "OPEN");
        join(scope, recipient, "CHANNEL", channel.id());
        String clientMsgId = "pg-idem-" + scope.unique();

        MessageView first = imFacade.send(send(scope, sender, channel.mainConversationId(), clientMsgId, "hello"));
        MessageView duplicate = imFacade.send(send(scope, sender, channel.mainConversationId(), clientMsgId, "ignored"));

        assertThat(duplicate.id()).as("duplicate client_msg_id returns original message id").isEqualTo(first.id());
        assertThat(duplicate.messageSeq()).as("duplicate client_msg_id returns original sequence").isEqualTo(first.messageSeq());
        assertThat(count("""
                select count(*)
                from im_message
                where conversation_id = ?
                  and sender_user_id = ?
                  and client_msg_id = ?
                  and deleted = false
                """, channel.mainConversationId(), sender, clientMsgId))
                .as("exactly one im_message row for the idempotency key")
                .isEqualTo(1L);
        assertThat(count("""
                select count(*)
                from im_outbox
                where message_id = ?
                  and event_type = 'MESSAGE_CREATED'
                  and deleted = false
                """, first.id()))
                .as("exactly one MESSAGE_CREATED outbox row for the idempotent message")
                .isEqualTo(1L);
    }

    @Test
    void concurrentSendsAllocateGapFreeSequencesOnPostgreSql() throws Exception {
        TestScope scope = scope("seq");
        Long firstSender = scope.user(1);
        Long secondSender = scope.user(2);
        ChannelView channel = channel(scope, firstSender, "channel", "OPEN");
        join(scope, secondSender, "CHANNEL", channel.id());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            Future<MessageView> first = executorService.submit(concurrentSend(
                    ready, start, scope, firstSender, channel.mainConversationId(), "pg-seq-a-" + scope.unique()));
            Future<MessageView> second = executorService.submit(concurrentSend(
                    ready, start, scope, secondSender, channel.mainConversationId(), "pg-seq-b-" + scope.unique()));

            assertThat(ready.await(5, TimeUnit.SECONDS)).as("both send tasks are ready").isTrue();
            start.countDown();

            List<Long> sequences = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))
                    .stream()
                    .map(MessageView::messageSeq)
                    .sorted()
                    .toList();

            assertThat(sequences).as("concurrent sends allocate sequence 1 and 2 without gaps").containsExactly(1L, 2L);
            assertThat(queryLong("select message_seq from im_conversation where id = ?", channel.mainConversationId()))
                    .as("conversation message_seq advances to the latest allocated sequence")
                    .isEqualTo(2L);
            assertThat(jdbcTemplate.queryForList("""
                    select message_seq
                    from im_message
                    where conversation_id = ?
                      and deleted = false
                    order by message_seq
                    """, Long.class, channel.mainConversationId()))
                    .containsExactly(1L, 2L);
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void markReadOnlyAdvancesCursorAndConversationListUnreadCountOnPostgreSql() {
        TestScope scope = scope("read");
        Long owner = scope.user(1);
        Long reader = scope.user(2);
        Long otherSender = scope.user(3);
        ChannelView channel = channel(scope, owner, "channel", "OPEN");
        join(scope, reader, "CHANNEL", channel.id());
        join(scope, otherSender, "CHANNEL", channel.id());
        imFacade.send(send(scope, owner, channel.mainConversationId(), "pg-read-1-" + scope.unique(), "one"));
        imFacade.send(send(scope, owner, channel.mainConversationId(), "pg-read-2-" + scope.unique(), "two"));
        imFacade.send(send(scope, owner, channel.mainConversationId(), "pg-read-3-" + scope.unique(), "three"));

        UnreadView atThree = imFacade.markRead(new MarkReadCommand(
                principal(scope, reader), channel.mainConversationId(), 3L));
        UnreadView atOne = imFacade.markRead(new MarkReadCommand(
                principal(scope, reader), channel.mainConversationId(), 1L));

        assertThat(atThree.lastReadSeq()).isEqualTo(3L);
        assertThat(atOne.lastReadSeq()).as("mark-read cannot move the cursor backwards").isEqualTo(3L);
        assertThat(queryLong("""
                select last_read_seq
                from im_conversation_member
                where conversation_id = ?
                  and user_id = ?
                  and status = 'ACTIVE'
                  and deleted = false
                """, channel.mainConversationId(), reader))
                .isEqualTo(3L);
        assertThat(conversation(scope, reader, channel.mainConversationId()).unreadCount()).isZero();

        imFacade.send(send(scope, otherSender, channel.mainConversationId(), "pg-read-4-" + scope.unique(), "four"));

        assertThat(conversation(scope, reader, channel.mainConversationId()).unreadCount())
                .as("one message after the reader cursor is unread")
                .isEqualTo(1L);
    }

    @Test
    void approvalGroupJoinActivatesMembershipConversationMemberAndMemberCountOnPostgreSql() {
        TestScope scope = scope("approval");
        Long owner = scope.user(1);
        Long applicant = scope.user(2);
        GroupView group = group(scope, owner, "group", "APPROVAL");

        JoinResultView pending = join(scope, applicant, "GROUP", group.id());

        assertThat(pending.status()).isEqualTo("PENDING");
        assertThat(count("""
                select count(*)
                from im_join_request
                where id = ?
                  and surface_type = 'GROUP'
                  and surface_id = ?
                  and user_id = ?
                  and status = 'PENDING'
                  and deleted = false
                """, pending.joinRequestId(), group.id(), applicant))
                .as("pending join request exists")
                .isEqualTo(1L);

        JoinResultView approved = roomFacade.approveJoin(new ApproveCommand(principal(scope, owner), pending.joinRequestId()));

        assertThat(approved.status()).isEqualTo("ACTIVE");
        assertThat(count("""
                select count(*)
                from im_membership
                where surface_type = 'GROUP'
                  and surface_id = ?
                  and user_id = ?
                  and status = 'ACTIVE'
                  and deleted = false
                """, group.id(), applicant))
                .as("approved applicant has active group membership")
                .isEqualTo(1L);
        assertThat(count("""
                select count(*)
                from im_conversation_member
                where conversation_id = ?
                  and user_id = ?
                  and status = 'ACTIVE'
                  and deleted = false
                """, group.conversationId(), applicant))
                .as("approved applicant has active conversation member")
                .isEqualTo(1L);
        assertThat(queryInteger("select member_count from im_group where id = ?", group.id()))
                .as("owner plus approved applicant increments member_count once")
                .isEqualTo(2);
    }

    @Test
    void muteGlobalMuteAndBanAreEnforcedOnPostgreSql() {
        TestScope scope = scope("gov");
        Long owner = scope.user(1);
        Long member = scope.user(2);
        Long dmPeer = scope.user(3);
        ChannelView channel = channel(scope, owner, "channel", "OPEN");
        join(scope, member, "CHANNEL", channel.id());

        govFacade.mute(new MuteUserCommand(
                principal(scope, owner), "CHANNEL", channel.id(), member, Instant.now().plusSeconds(600), "noise"));

        assertImExceptionCode(
                () -> imFacade.send(send(scope, member, channel.mainConversationId(), "pg-muted-" + scope.unique(), "muted")),
                "IM_SEND_DENIED");

        govFacade.mute(new MuteUserCommand(
                principal(scope, owner), "CHANNEL", channel.id(), member, Instant.now().minusSeconds(60), "expired"));

        assertThat(imFacade.send(send(
                scope, member, channel.mainConversationId(), "pg-unmuted-" + scope.unique(), "unmuted")).messageSeq())
                .isEqualTo(1L);

        GroupView group = group(scope, owner, "global-group", "OPEN");
        join(scope, member, "GROUP", group.id());
        ConversationView dm = dmFacade.openDm(new OpenDmCommand(principal(scope, member), dmPeer));
        govFacade.globalMute(new GlobalMuteCommand(
                principal(scope, owner), "PLATFORM", null, member, Instant.now().plusSeconds(600), "platform"));

        assertImExceptionCode(
                () -> imFacade.send(send(scope, member, channel.mainConversationId(), "pg-global-channel-" + scope.unique(), "denied")),
                "IM_SEND_DENIED");
        assertImExceptionCode(
                () -> imFacade.send(send(scope, member, group.conversationId(), "pg-global-group-" + scope.unique(), "denied")),
                "IM_SEND_DENIED");
        assertImExceptionCode(
                () -> imFacade.send(send(scope, member, dm.id(), "pg-global-dm-" + scope.unique(), "denied")),
                "IM_SEND_DENIED");

        Long bannedMember = scope.user(4);
        GroupView banGroup = group(scope, owner, "ban-group", "OPEN");
        join(scope, bannedMember, "GROUP", banGroup.id());
        govFacade.ban(new BanUserCommand(principal(scope, owner), "GROUP", banGroup.id(), bannedMember, "abuse"));

        assertImExceptionCode(() -> join(scope, bannedMember, "GROUP", banGroup.id()), "IM_MEMBER_BANNED");
    }

    @Test
    void dmBlockFreezesSendsButKeepsPriorHistoryReadableOnPostgreSql() {
        TestScope scope = scope("dm");
        Long userA = scope.user(1);
        Long userB = scope.user(2);
        ConversationView dm = dmFacade.openDm(new OpenDmCommand(principal(scope, userA), userB));
        MessageView first = imFacade.send(send(scope, userA, dm.id(), "pg-dm-first-" + scope.unique(), "before block"));

        dmFacade.block(new BlockUserCommand(principal(scope, userA), userB, "spam"));

        assertImExceptionCode(
                () -> imFacade.send(send(scope, userA, dm.id(), "pg-dm-a-frozen-" + scope.unique(), "frozen")),
                "IM_SEND_DENIED");
        assertImExceptionCode(
                () -> imFacade.send(send(scope, userB, dm.id(), "pg-dm-b-frozen-" + scope.unique(), "frozen")),
                "IM_SEND_DENIED");
        assertThat(history(scope, userA, dm.id()).getContent()).extracting(MessageView::id).contains(first.id());
        assertThat(history(scope, userB, dm.id()).getContent()).extracting(MessageView::id).contains(first.id());

        dmFacade.block(new BlockUserCommand(principal(scope, userB), userA, "reverse"));
        dmFacade.unblock(new BlockUserCommand(principal(scope, userA), userB, null));

        assertImExceptionCode(
                () -> imFacade.send(send(scope, userA, dm.id(), "pg-dm-still-frozen-" + scope.unique(), "still frozen")),
                "IM_SEND_DENIED");

        dmFacade.unblock(new BlockUserCommand(principal(scope, userB), userA, null));

        MessageView afterUnblock = imFacade.send(send(
                scope, userA, dm.id(), "pg-dm-after-unblock-" + scope.unique(), "after unblock"));
        assertThat(afterUnblock.messageSeq()).isEqualTo(2L);
    }

    @Test
    void historyVisibilityRulesAreEnforcedOnPostgreSql() {
        TestScope scope = scope("vis");
        Long owner = scope.user(1);
        Long nonMember = scope.user(2);
        Long groupMember = scope.user(3);
        Long dmUser = scope.user(4);
        Long dmPeer = scope.user(5);
        Long outsider = scope.user(6);
        ChannelView channel = channel(scope, owner, "channel", "OPEN");
        MessageView channelMessage = imFacade.send(send(
                scope, owner, channel.mainConversationId(), "pg-vis-channel-" + scope.unique(), "public"));
        GroupView group = group(scope, owner, "group", "OPEN");
        MessageView groupMessage = imFacade.send(send(
                scope, owner, group.conversationId(), "pg-vis-group-" + scope.unique(), "private"));
        ConversationView dm = dmFacade.openDm(new OpenDmCommand(principal(scope, dmUser), dmPeer));
        imFacade.send(send(scope, dmUser, dm.id(), "pg-vis-dm-" + scope.unique(), "dm"));

        assertThat(history(scope, nonMember, channel.mainConversationId()).getContent())
                .extracting(MessageView::id)
                .containsExactly(channelMessage.id());
        assertImExceptionCode(() -> history(scope, nonMember, group.conversationId()), "IM_HISTORY_FORBIDDEN");
        assertImExceptionCode(() -> history(scope, outsider, dm.id()), "IM_HISTORY_FORBIDDEN");

        join(scope, groupMember, "GROUP", group.id());

        assertThat(history(scope, groupMember, group.conversationId()).getContent())
                .extracting(MessageView::id)
                .containsExactly(groupMessage.id());
    }

    private Callable<MessageView> concurrentSend(CountDownLatch ready, CountDownLatch start, TestScope scope,
                                                 Long userId, Long conversationId, String clientMsgId) {
        return () -> {
            ready.countDown();
            assertThat(start.await(5, TimeUnit.SECONDS)).as("send task released").isTrue();
            return imFacade.send(send(scope, userId, conversationId, clientMsgId, clientMsgId));
        };
    }

    private ChannelView channel(TestScope scope, Long ownerUserId, String suffix, String joinPolicy) {
        return roomFacade.createChannel(new CreateChannelCommand(
                principal(scope, ownerUserId), scope.contextType(), null, scope.key(suffix), scope.key(suffix),
                joinPolicy, "{}"));
    }

    private GroupView group(TestScope scope, Long ownerUserId, String suffix, String joinPolicy) {
        return roomFacade.createGroup(new CreateGroupCommand(
                principal(scope, ownerUserId), null, scope.contextType(), null, scope.key(suffix), scope.key(suffix),
                joinPolicy, "{}"));
    }

    private JoinResultView join(TestScope scope, Long userId, String surfaceType, Long surfaceId) {
        return roomFacade.applyJoin(new JoinCommand(principal(scope, userId), surfaceType, surfaceId, "join"));
    }

    private SendMessageCommand send(TestScope scope, Long userId, Long conversationId, String clientMsgId,
                                    String content) {
        return new SendMessageCommand(principal(scope, userId), conversationId, clientMsgId, "TEXT", content, "{}", "{}");
    }

    private Page<MessageView> history(TestScope scope, Long userId, Long conversationId) {
        return imFacade.history(new HistoryQuery(principal(scope, userId), conversationId, 0, 20, null, null));
    }

    private ConversationView conversation(TestScope scope, Long userId, Long conversationId) {
        return imFacade.listConversations(new ListConversationsQuery(principal(scope, userId), scope.contextType(), null))
                .stream()
                .filter(view -> conversationId.equals(view.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("conversation " + conversationId + " not found in list"));
    }

    private ImPrincipal principal(TestScope scope, Long userId) {
        return new ImPrincipal(userId, Set.of(), scope.contextType(), Map.of());
    }

    private TestScope scope(String scenario) {
        long unique = UNIQUE.incrementAndGet();
        return new TestScope(CONTEXT_PREFIX + "_" + scenario.toUpperCase() + "_" + unique,
                "pg-" + scenario + "-" + unique, unique * 100);
    }

    private void deleteOnlyImRows() {
        for (String table : DATA_CLEANUP_ORDER) {
            jdbcTemplate.update("delete from %s.%s".formatted(SCHEMA_NAME, table));
        }
    }

    private void assertUsingIsolatedSchema() {
        String currentSchema = jdbcTemplate.queryForObject("select current_schema()", String.class);
        assertThat(currentSchema)
                .as("ImPostgresBehaviorIT must use its random schema before deleting IM rows")
                .isEqualTo(SCHEMA_NAME);
    }

    private Long count(String sql, Object... args) {
        return queryLong(sql, args);
    }

    private Long queryLong(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private Integer queryInteger(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private void assertImExceptionCode(ThrowingCallable callable, String expectedCode) {
        assertThatThrownBy(callable)
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo(expectedCode);
    }

    private record TestScope(String contextType, String keyPrefix, long baseUserId) {

        Long user(long offset) {
            return baseUserId + offset;
        }

        String key(String suffix) {
            return keyPrefix + "-" + suffix;
        }

        long unique() {
            return baseUserId;
        }
    }

    private static String newSchemaName(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    static class PostgresTestProperties implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            String url = System.getenv(ENV_URL);
            String username = System.getenv(ENV_USERNAME);
            String password = System.getenv(ENV_PASSWORD);
            List<String> missing = List.of(ENV_URL, ENV_USERNAME, ENV_PASSWORD)
                    .stream()
                    .filter(name -> !hasText(System.getenv(name)))
                    .sorted()
                    .toList();
            if (!missing.isEmpty()) {
                throw new IllegalStateException("""
                        ImPostgresBehaviorIT requires IM_POSTGRES_TEST_URL, IM_POSTGRES_TEST_USERNAME, \
                        and IM_POSTGRES_TEST_PASSWORD; missing: %s""".formatted(String.join(", ", missing)));
            }
            createSchema(url, username, password);
            TestPropertyValues.of(
                    "spring.datasource.url=" + url,
                    "spring.datasource.username=" + username,
                    "spring.datasource.password=" + password,
                    "spring.datasource.driver-class-name=org.postgresql.Driver",
                    "spring.datasource.hikari.connection-init-sql=SET search_path TO " + SCHEMA_NAME + ", public",
                    "spring.jpa.hibernate.ddl-auto=validate",
                    "spring.jpa.properties.hibernate.default_schema=" + SCHEMA_NAME,
                    "spring.flyway.enabled=true",
                    "spring.flyway.locations=classpath:db/migration,classpath:db/postgresql",
                    "spring.flyway.schemas=" + SCHEMA_NAME,
                    "spring.flyway.default-schema=" + SCHEMA_NAME,
                    "spring.flyway.init-sqls[0]=SET search_path TO " + SCHEMA_NAME + ", public",
                    "spring.flyway.clean-disabled=true",
                    "spring.ai.dashscope.api-key=test-api-key",
                    "spring.ai.vectorstore.type=none",
                    "agent.mcp.runtime.enabled=false",
                    "management.health.redis.enabled=false",
                    "spring.data.redis.repositories.enabled=false",
                    "mario.rbac.resource-sync.enabled=false",
                    "mario.rbac.cache.enabled=false",
                    "mario.agent.memory.checkpointer.enabled=false"
            ).applyTo(applicationContext);
        }

        private static boolean hasText(String value) {
            return value != null && !value.trim().isEmpty();
        }

        private static void createSchema(String url, String username, String password) {
            try (Connection connection = DriverManager.getConnection(url, username, password);
                 Statement statement = connection.createStatement()) {
                statement.execute("create schema if not exists " + SCHEMA_NAME);
            } catch (SQLException ex) {
                throw new IllegalStateException("""
                        ImPostgresBehaviorIT could not create isolated PostgreSQL schema %s using \
                        IM_POSTGRES_TEST_URL, IM_POSTGRES_TEST_USERNAME, and IM_POSTGRES_TEST_PASSWORD: %s"""
                        .formatted(SCHEMA_NAME, rootMessage(ex)), ex);
            }
        }

        private static String rootMessage(Throwable throwable) {
            Throwable current = throwable;
            while (current.getCause() != null) {
                current = current.getCause();
            }
            return current.getMessage() == null ? throwable.toString() : current.getMessage();
        }
    }
}
