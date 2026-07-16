package top.egon.mario.im;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.mario.im.facade.DmFacade;
import top.egon.mario.im.facade.ImFacade;
import top.egon.mario.im.facade.RoomFacade;
import top.egon.mario.im.facade.dto.command.JoinCommand;
import top.egon.mario.im.facade.dto.command.OpenDmCommand;
import top.egon.mario.im.facade.dto.command.SendMessageCommand;
import top.egon.mario.im.facade.dto.view.ChannelView;
import top.egon.mario.im.facade.dto.view.ConversationView;
import top.egon.mario.im.platform.PlatformRoomFacade;
import top.egon.mario.im.po.ImOutboxPo;
import top.egon.mario.im.po.enums.ImOutboxStatus;
import top.egon.mario.im.policy.ImPrincipal;
import top.egon.mario.im.realtime.ImConnectionRegistry;
import top.egon.mario.im.realtime.LocalRealtimeRouter;
import top.egon.mario.im.realtime.OutboxDispatcher;
import top.egon.mario.im.realtime.RealtimeRouter;
import top.egon.mario.im.repository.ImConversationMemberRepository;
import top.egon.mario.im.repository.ImOutboxRepository;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.ai.dashscope.api-key=test-api-key",
        "im.realtime.dispatcher.enabled=true",
        "im.realtime.dispatcher.runner.enabled=false"
})
class ImOutboxDispatcherTests {

    private static final String CONTEXT_TYPE = "IM_OUTBOX_DISPATCHER_TEST";

    @Autowired
    private DmFacade dmFacade;

    @Autowired
    private ImFacade imFacade;

    @Autowired
    private RoomFacade roomFacade;

    @Autowired
    private PlatformRoomFacade platformRoomFacade;

    @Autowired
    private OutboxDispatcher outboxDispatcher;

    @Autowired
    private RecordingRealtimeRouter realtimeRouter;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ImConversationMemberRepository conversationMemberRepository;

    @Autowired
    private ImOutboxRepository outboxRepository;

    @Autowired
    private DataSourceProperties dataSourceProperties;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        retireExistingPendingOutboxes();
        realtimeRouter.reset();
    }

    @Test
    void dispatchBatchMarksPendingRowsDispatchedWhenRealtimeDeliverySucceeds() {
        ConversationView dm = dm(9301L, 9302L);
        imFacade.send(send(9301L, dm.id(), "dispatch-success-1"));
        imFacade.send(send(9301L, dm.id(), "dispatch-success-2"));
        List<ImOutboxPo> rows = pendingRowsFor(dm.id());
        makeImmediatelyAvailable(rows);

        int dispatched = outboxDispatcher.dispatchBatch(10);

        assertThat(dispatched).isEqualTo(2);
        assertThat(reload(rows))
                .hasSize(2)
                .allSatisfy(outbox -> {
                    assertThat(outbox.getStatus()).isEqualTo(ImOutboxStatus.DISPATCHED);
                    assertThat(outbox.getAttempts()).isEqualTo(0);
                    assertThat(outbox.getLastError()).isNull();
                });
        assertThat(realtimeRouter.deliveredOutboxIds())
                .containsExactlyElementsOf(rows.stream().map(ImOutboxPo::getId).toList());
    }

    @Test
    void dispatchBatchRetriesPendingRowWhenRealtimeDeliveryFails() {
        ConversationView dm = dm(9311L, 9312L);
        imFacade.send(send(9311L, dm.id(), "dispatch-fail-1"));
        ImOutboxPo row = pendingRowsFor(dm.id()).getFirst();
        row.setAvailableAt(Instant.EPOCH);
        outboxRepository.saveAndFlush(row);
        realtimeRouter.failDeliveries();
        Instant beforeDispatch = Instant.now();

        int dispatched = outboxDispatcher.dispatchBatch(1);

        ImOutboxPo failed = outboxRepository.findByIdAndDeletedFalse(row.getId()).orElseThrow();
        assertThat(dispatched).isZero();
        assertThat(failed.getAttempts()).isEqualTo(1);
        assertThat(failed.getStatus()).isEqualTo(ImOutboxStatus.PENDING);
        assertThat(failed.getAvailableAt()).isAfter(beforeDispatch);
        assertThat(failed.getLastError()).contains("forced realtime failure");
    }

    @Test
    void dispatchBatchMarksRowFailedAfterMaxAttempts() {
        ConversationView dm = dm(9313L, 9314L);
        imFacade.send(send(9313L, dm.id(), "dispatch-fail-final"));
        ImOutboxPo row = pendingRowsFor(dm.id()).getFirst();
        row.setAttempts(2);
        row.setAvailableAt(Instant.EPOCH);
        outboxRepository.saveAndFlush(row);
        realtimeRouter.failDeliveries();

        int dispatched = outboxDispatcher.dispatchBatch(1);

        ImOutboxPo failed = outboxRepository.findByIdAndDeletedFalse(row.getId()).orElseThrow();
        assertThat(dispatched).isZero();
        assertThat(failed.getAttempts()).isEqualTo(3);
        assertThat(failed.getStatus()).isEqualTo(ImOutboxStatus.FAILED);
        assertThat(failed.getLastError()).contains("forced realtime failure");
    }

    @Test
    void concurrentDispatcherWorkersDoNotDeliverTheSameOutboxRowTwice() throws Exception {
        ConversationView dm = dm(9321L, 9322L);
        imFacade.send(send(9321L, dm.id(), "dispatch-once-1"));
        ImOutboxPo row = pendingRowsFor(dm.id()).getFirst();
        row.setAvailableAt(Instant.EPOCH);
        outboxRepository.saveAndFlush(row);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executorService.submit(worker(ready, start));
            Future<Integer> second = executorService.submit(worker(ready, start));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(realtimeRouter.deliveredOutboxIds()).containsExactly(row.getId());
            assertThat(outboxRepository.findByIdAndDeletedFalse(row.getId())).get()
                    .extracting(ImOutboxPo::getStatus)
                    .isEqualTo(ImOutboxStatus.DISPATCHED);
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void dispatcherRunnerIsOptedOutForDeterministicDispatcherTests() {
        assertThat(applicationContext.containsBean("outboxDispatcherRunner")).isFalse();
    }

    @Test
    void dispatcherRunnerTriggersDispatchBatchesUntilStopped() throws Exception {
        CountDownLatch dispatched = new CountDownLatch(1);
        OutboxDispatcher dispatcher = mock(OutboxDispatcher.class);
        when(dispatcher.dispatchBatch(5)).thenAnswer(invocation -> {
            dispatched.countDown();
            return 0;
        });
        SmartLifecycle runner = newOutboxDispatcherRunner(dispatcher, 5, 0L, 10L);
        try {
            runner.start();

            assertThat(dispatched.await(2, TimeUnit.SECONDS)).isTrue();
        } finally {
            runner.stop();
        }
    }

    @Test
    void dispatcherDisabledByDefaultDoesNotCreateDispatcherOrRunner() {
        new ApplicationContextRunner()
                .withUserConfiguration(OutboxDispatcher.class, outboxDispatcherRunnerClass())
                .run(context -> {
                    assertThat(context).doesNotHaveBean(OutboxDispatcher.class);
                    assertThat(context).doesNotHaveBean("outboxDispatcherRunner");
                });
    }

    @Test
    void postgresDispatcherClaimsRowsWithNativeSkipLockedMethod() {
        ImOutboxRepository repository = mock(ImOutboxRepository.class);
        RealtimeRouter router = mock(RealtimeRouter.class);
        DataSourceProperties dataSourceProperties = new DataSourceProperties();
        dataSourceProperties.setUrl("jdbc:postgresql://localhost:5432/cyber_mario");
        when(repository.claimPendingForDispatch(any(Instant.class), any(Pageable.class))).thenReturn(List.of());
        when(repository.claimPendingForDispatchPostgreSql(any(Instant.class), eq(10))).thenReturn(List.of());

        new ApplicationContextRunner()
                .withPropertyValues("im.realtime.dispatcher.enabled=true")
                .withBean(ImOutboxRepository.class, () -> repository)
                .withBean(RealtimeRouter.class, () -> router)
                .withBean(DataSourceProperties.class, () -> dataSourceProperties)
                .withUserConfiguration(OutboxDispatcher.class)
                .run(context -> {
                    context.getBean(OutboxDispatcher.class).dispatchBatch(10);

                    verify(repository).claimPendingForDispatchPostgreSql(any(Instant.class), eq(10));
                    verify(repository, never()).claimPendingForDispatch(any(Instant.class), any(Pageable.class));
                });
    }

    @Test
    void localRealtimeRouterDeliversByUserAndConversationAndTreatsMissingLocalConnectionsAsSuccess() throws Exception {
        ConversationView dm = dm(9331L, 9332L);
        ImConnectionRegistry registry = new ImConnectionRegistry();
        LocalRealtimeRouter router = new LocalRealtimeRouter(registry, conversationMemberRepository);
        List<Map<String, Object>> firstUserFrames = new CopyOnWriteArrayList<>();
        List<Map<String, Object>> secondUserFrames = new CopyOnWriteArrayList<>();
        try (var first = registry.register(9331L, firstUserFrames::add);
             var second = registry.register(9332L, secondUserFrames::add)) {
            Map<String, Object> directFrame = Map.of("eventType", "DIRECT");
            Map<String, Object> conversationFrame = Map.of("eventType", "CONVERSATION");

            router.deliverToUser(9331L, directFrame);
            router.deliverToUser(9399L, directFrame);
            router.deliverToConversation(dm.id(), conversationFrame);

            assertThat(firstUserFrames).containsExactly(directFrame, conversationFrame);
            assertThat(secondUserFrames).containsExactly(conversationFrame);
        }
    }

    @Test
    void committedPublicChannelEventReachesEveryMemberConnectionButNotNonmemberReader() {
        long ownerUserId = 9341L;
        long memberUserId = 9342L;
        long nonmemberUserId = 9343L;
        ChannelView channel = platformRoomFacade.createGeneralChannel(
                platformPrincipal(ownerUserId), "realtime-general-9341", "Realtime General");
        roomFacade.applyJoin(new JoinCommand(
                platformPrincipal(memberUserId), "CHANNEL", channel.id(), "join realtime test"));
        imFacade.send(new SendMessageCommand(
                platformPrincipal(ownerUserId), channel.mainConversationId(), "realtime-general-message",
                "TEXT", "hello realtime", "{}", "{}"));
        ImOutboxPo outbox = pendingRowsFor(channel.mainConversationId()).getFirst();
        makeImmediatelyAvailable(List.of(outbox));

        ImConnectionRegistry registry = new ImConnectionRegistry();
        LocalRealtimeRouter router = new LocalRealtimeRouter(registry, conversationMemberRepository);
        OutboxDispatcher dispatcher = new OutboxDispatcher(
                outboxRepository, router, dataSourceProperties, 3, 1000L);
        List<Map<String, Object>> ownerFirstFrames = new CopyOnWriteArrayList<>();
        List<Map<String, Object>> ownerSecondFrames = new CopyOnWriteArrayList<>();
        List<Map<String, Object>> memberFrames = new CopyOnWriteArrayList<>();
        List<Map<String, Object>> nonmemberFrames = new CopyOnWriteArrayList<>();

        try (var ownerFirst = registry.register(ownerUserId, ownerFirstFrames::add);
             var ownerSecond = registry.register(ownerUserId, ownerSecondFrames::add);
             var member = registry.register(memberUserId, memberFrames::add);
             var nonmember = registry.register(nonmemberUserId, nonmemberFrames::add)) {
            Integer dispatched = new TransactionTemplate(transactionManager)
                    .execute(status -> dispatcher.dispatchBatch(1));
            assertThat(dispatched).isEqualTo(1);
        }

        assertThat(ownerFirstFrames).singleElement()
                .extracting(frame -> frame.get("outboxId"))
                .isEqualTo(outbox.getId());
        assertThat(ownerSecondFrames).singleElement()
                .extracting(frame -> frame.get("outboxId"))
                .isEqualTo(outbox.getId());
        assertThat(memberFrames).singleElement()
                .extracting(frame -> frame.get("outboxId"))
                .isEqualTo(outbox.getId());
        assertThat(nonmemberFrames).isEmpty();
        assertThat(outboxRepository.findByIdAndDeletedFalse(outbox.getId())).get()
                .extracting(ImOutboxPo::getStatus)
                .isEqualTo(ImOutboxStatus.DISPATCHED);
    }

    private Callable<Integer> worker(CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
            return outboxDispatcher.dispatchBatch(1);
        };
    }

    private SmartLifecycle newOutboxDispatcherRunner(OutboxDispatcher dispatcher, int batchSize,
                                                     long initialDelayMillis, long fixedDelayMillis) throws Exception {
        Constructor<?> constructor = outboxDispatcherRunnerClass()
                .getConstructor(OutboxDispatcher.class, int.class, long.class, long.class);
        return (SmartLifecycle) constructor.newInstance(dispatcher, batchSize, initialDelayMillis, fixedDelayMillis);
    }

    private Class<?> outboxDispatcherRunnerClass() {
        try {
            return Class.forName("top.egon.mario.im.realtime.OutboxDispatcherRunner");
        } catch (ClassNotFoundException ex) {
            throw new AssertionError("OutboxDispatcherRunner must provide the production dispatch trigger", ex);
        }
    }

    private ConversationView dm(Long userId, Long targetUserId) {
        return dmFacade.openDm(new OpenDmCommand(principal(userId), targetUserId));
    }

    private SendMessageCommand send(Long userId, Long conversationId, String clientMsgId) {
        return new SendMessageCommand(principal(userId), conversationId, clientMsgId, "TEXT", clientMsgId, "{}", "{}");
    }

    private List<ImOutboxPo> pendingRowsFor(Long conversationId) {
        return outboxRepository.findAll().stream()
                .filter(outbox -> conversationId.equals(outbox.getConversationId()))
                .filter(outbox -> ImOutboxStatus.PENDING.equals(outbox.getStatus()))
                .toList();
    }

    private void makeImmediatelyAvailable(List<ImOutboxPo> rows) {
        List<ImOutboxPo> available = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            ImOutboxPo row = rows.get(index);
            row.setAvailableAt(Instant.EPOCH.plusMillis(index));
            available.add(row);
        }
        outboxRepository.saveAllAndFlush(available);
    }

    private List<ImOutboxPo> reload(List<ImOutboxPo> rows) {
        return rows.stream()
                .map(row -> outboxRepository.findByIdAndDeletedFalse(row.getId()).orElseThrow())
                .toList();
    }

    private void retireExistingPendingOutboxes() {
        List<ImOutboxPo> pending = outboxRepository.findAll().stream()
                .filter(outbox -> ImOutboxStatus.PENDING.equals(outbox.getStatus()))
                .toList();
        pending.forEach(outbox -> outbox.setStatus(ImOutboxStatus.DISPATCHED));
        outboxRepository.saveAllAndFlush(pending);
    }

    private ImPrincipal principal(Long userId) {
        return new ImPrincipal(userId, Set.of(), CONTEXT_TYPE, Map.of());
    }

    private ImPrincipal platformPrincipal(Long userId) {
        return new ImPrincipal(userId, Set.of(), PlatformRoomFacade.PLATFORM_CONTEXT_TYPE, Map.of());
    }

    @TestConfiguration
    static class OutboxDispatcherTestConfiguration {

        @Bean
        @Primary
        RecordingRealtimeRouter recordingRealtimeRouter() {
            return new RecordingRealtimeRouter();
        }
    }

    static class RecordingRealtimeRouter implements RealtimeRouter {

        private final List<Delivery> deliveries = new CopyOnWriteArrayList<>();
        private final AtomicBoolean failing = new AtomicBoolean();

        @Override
        public void deliverToUser(Long userId, Map<String, Object> frame) {
            deliver("USER", userId, frame);
        }

        @Override
        public void deliverToConversation(Long conversationId, Map<String, Object> frame) {
            deliver("CONVERSATION", conversationId, frame);
        }

        void failDeliveries() {
            failing.set(true);
        }

        void reset() {
            deliveries.clear();
            failing.set(false);
        }

        List<Long> deliveredOutboxIds() {
            return deliveries.stream()
                    .map(delivery -> (Long) delivery.frame().get("outboxId"))
                    .toList();
        }

        private void deliver(String targetType, Long targetId, Map<String, Object> frame) {
            if (failing.get()) {
                throw new IllegalStateException("forced realtime failure");
            }
            deliveries.add(new Delivery(targetType, targetId, frame));
        }
    }

    private record Delivery(String targetType, Long targetId, Map<String, Object> frame) {
    }
}
