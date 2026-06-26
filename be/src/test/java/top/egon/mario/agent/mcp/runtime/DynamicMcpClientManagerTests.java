package top.egon.mario.agent.mcp.runtime;

import io.modelcontextprotocol.client.McpSyncClient;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import top.egon.mario.agent.mcp.po.McpServerConfigPo;
import top.egon.mario.agent.mcp.po.enums.McpServerStatus;
import top.egon.mario.agent.mcp.po.enums.McpTransportType;
import top.egon.mario.agent.mcp.repository.McpServerConfigRepository;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Verifies dynamic MCP client lifecycle behavior.
 */
class DynamicMcpClientManagerTests {

    @Test
    void refreshServerHoldsSameServerLockUntilTransactionCompletes() throws Exception {
        McpServerConfigRepository serverRepository = mock(McpServerConfigRepository.class);
        McpClientFactory clientFactory = mock(McpClientFactory.class);
        McpSyncClient firstClient = mock(McpSyncClient.class);
        McpSyncClient secondClient = mock(McpSyncClient.class);
        McpServerConfigPo server = server();
        CountDownLatch firstTransactionCallbackReturned = new CountDownLatch(1);
        CountDownLatch allowFirstTransactionComplete = new CountDownLatch(1);
        CountDownLatch secondRefreshStarted = new CountDownLatch(1);
        CountDownLatch secondTransactionEntered = new CountDownLatch(1);
        AtomicInteger transactionCalls = new AtomicInteger();
        given(serverRepository.findByIdAndDeletedFalse(9L)).willReturn(Optional.of(server));
        given(serverRepository.save(any(McpServerConfigPo.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(clientFactory.create(any(McpServerConfigPo.class))).willReturn(firstClient, secondClient);
        TransactionOperations transactionOperations = new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                int call = transactionCalls.incrementAndGet();
                if (call == 2) {
                    secondTransactionEntered.countDown();
                }
                T result = action.doInTransaction(new SimpleTransactionStatus());
                if (call == 1) {
                    firstTransactionCallbackReturned.countDown();
                    try {
                        assertThat(allowFirstTransactionComplete.await(5, TimeUnit.SECONDS))
                                .as("first transaction released")
                                .isTrue();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(e);
                    }
                }
                return result;
            }
        };
        DynamicMcpClientManager manager = new DynamicMcpClientManager(serverRepository, clientFactory,
                transactionOperations);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> firstRefresh = executor.submit(() -> manager.refreshServer(9L));
            assertThat(firstTransactionCallbackReturned.await(5, TimeUnit.SECONDS))
                    .as("first transactional callback returned before commit completed")
                    .isTrue();

            Future<?> secondRefresh = executor.submit(() -> {
                secondRefreshStarted.countDown();
                manager.refreshServer(9L);
            });
            assertThat(secondRefreshStarted.await(5, TimeUnit.SECONDS))
                    .as("second refresh task started")
                    .isTrue();
            assertThat(secondTransactionEntered.await(300, TimeUnit.MILLISECONDS))
                    .as("second same-server refresh waits for first transaction completion")
                    .isFalse();

            allowFirstTransactionComplete.countDown();
            firstRefresh.get(5, TimeUnit.SECONDS);
            secondRefresh.get(5, TimeUnit.SECONDS);

            assertThat(secondTransactionEntered.getCount())
                    .as("second refresh eventually entered transactional work")
                    .isZero();
            assertThat(manager.client(9L)).containsSame(secondClient);
        } finally {
            allowFirstTransactionComplete.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void refreshServerSerializesSameServerClientCreation() throws Exception {
        McpServerConfigRepository serverRepository = mock(McpServerConfigRepository.class);
        McpClientFactory clientFactory = mock(McpClientFactory.class);
        McpSyncClient olderClient = mock(McpSyncClient.class);
        McpSyncClient newerClient = mock(McpSyncClient.class);
        McpServerConfigPo server = server();
        CountDownLatch olderCreateEntered = new CountDownLatch(1);
        CountDownLatch allowOlderCreateReturn = new CountDownLatch(1);
        CountDownLatch newerRefreshStarted = new CountDownLatch(1);
        CountDownLatch newerCreateEntered = new CountDownLatch(1);
        CountDownLatch allowNewerCreateReturn = new CountDownLatch(1);
        AtomicInteger createCalls = new AtomicInteger();
        AtomicInteger activeCreates = new AtomicInteger();
        AtomicBoolean concurrentCreateSeen = new AtomicBoolean();
        given(serverRepository.findByIdAndDeletedFalse(9L)).willReturn(Optional.of(server));
        given(serverRepository.save(any(McpServerConfigPo.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(clientFactory.create(any(McpServerConfigPo.class))).willAnswer(invocation -> {
            int call = createCalls.incrementAndGet();
            if (activeCreates.incrementAndGet() > 1) {
                concurrentCreateSeen.set(true);
            }
            try {
                if (call == 1) {
                    olderCreateEntered.countDown();
                    assertThat(allowOlderCreateReturn.await(5, TimeUnit.SECONDS))
                            .as("older refresh released")
                            .isTrue();
                    return olderClient;
                }
                if (call == 2) {
                    newerCreateEntered.countDown();
                    assertThat(allowNewerCreateReturn.await(5, TimeUnit.SECONDS))
                            .as("newer refresh released")
                            .isTrue();
                    return newerClient;
                }
                throw new AssertionError("unexpected client creation call " + call);
            } finally {
                activeCreates.decrementAndGet();
            }
        });
        DynamicMcpClientManager manager = new DynamicMcpClientManager(serverRepository, clientFactory);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> olderRefresh = executor.submit(() -> manager.refreshServer(9L));
            assertThat(olderCreateEntered.await(5, TimeUnit.SECONDS))
                    .as("older refresh entered client creation")
                    .isTrue();

            Future<?> newerRefresh = executor.submit(() -> {
                newerRefreshStarted.countDown();
                manager.refreshServer(9L);
            });
            assertThat(newerRefreshStarted.await(5, TimeUnit.SECONDS))
                    .as("newer refresh task started")
                    .isTrue();
            assertThat(newerCreateEntered.await(300, TimeUnit.MILLISECONDS))
                    .as("newer same-server refresh waits for older client creation")
                    .isFalse();

            allowOlderCreateReturn.countDown();
            olderRefresh.get(5, TimeUnit.SECONDS);
            assertThat(newerCreateEntered.await(5, TimeUnit.SECONDS))
                    .as("newer refresh enters after older refresh completes")
                    .isTrue();
            allowNewerCreateReturn.countDown();
            newerRefresh.get(5, TimeUnit.SECONDS);

            assertThat(concurrentCreateSeen).isFalse();
            assertThat(manager.client(9L)).containsSame(newerClient);
        } finally {
            allowOlderCreateReturn.countDown();
            allowNewerCreateReturn.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void refreshServerInstallsNewClientWhenExistingCloseFails() {
        McpServerConfigRepository serverRepository = mock(McpServerConfigRepository.class);
        McpClientFactory clientFactory = mock(McpClientFactory.class);
        McpSyncClient oldClient = mock(McpSyncClient.class);
        McpSyncClient newClient = mock(McpSyncClient.class);
        McpServerConfigPo server = server();
        given(serverRepository.findByIdAndDeletedFalse(9L)).willReturn(Optional.of(server));
        given(serverRepository.save(any(McpServerConfigPo.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(clientFactory.create(server)).willReturn(oldClient, newClient);
        DynamicMcpClientManager manager = new DynamicMcpClientManager(serverRepository, clientFactory);
        manager.refreshServer(9L);
        willThrow(new RuntimeException("graceful close failed")).given(oldClient).closeGracefully();
        willThrow(new RuntimeException("close failed")).given(oldClient).close();

        manager.refreshServer(9L);

        assertThat(manager.client(9L)).containsSame(newClient);
        assertThat(server.getStatus()).isEqualTo(McpServerStatus.CONNECTED);
        assertThat(server.getLastError()).isNull();
    }

    @Test
    void disableServerDisablesPersistenceWhenExistingCloseFails() {
        McpServerConfigRepository serverRepository = mock(McpServerConfigRepository.class);
        McpClientFactory clientFactory = mock(McpClientFactory.class);
        McpSyncClient oldClient = mock(McpSyncClient.class);
        McpServerConfigPo server = server();
        given(serverRepository.findByIdAndDeletedFalse(9L)).willReturn(Optional.of(server));
        given(serverRepository.save(any(McpServerConfigPo.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(clientFactory.create(server)).willReturn(oldClient);
        DynamicMcpClientManager manager = new DynamicMcpClientManager(serverRepository, clientFactory);
        manager.refreshServer(9L);
        willThrow(new RuntimeException("graceful close failed")).given(oldClient).closeGracefully();
        willThrow(new RuntimeException("close failed")).given(oldClient).close();

        manager.disableServer(9L);

        assertThat(manager.client(9L)).isEmpty();
        assertThat(server.isEnabled()).isFalse();
        assertThat(server.getStatus()).isEqualTo(McpServerStatus.DISABLED);
    }

    @Test
    void shutdownClosesInstalledClients() {
        McpServerConfigRepository serverRepository = mock(McpServerConfigRepository.class);
        McpClientFactory clientFactory = mock(McpClientFactory.class);
        McpSyncClient client = mock(McpSyncClient.class);
        McpServerConfigPo server = server();
        given(serverRepository.findByIdAndDeletedFalse(9L)).willReturn(Optional.of(server));
        given(serverRepository.save(any(McpServerConfigPo.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(clientFactory.create(server)).willReturn(client);
        DynamicMcpClientManager manager = new DynamicMcpClientManager(serverRepository, clientFactory);
        manager.refreshServer(9L);

        manager.shutdown();

        verify(client).closeGracefully();
        assertThat(manager.client(9L)).isEmpty();
    }

    @Test
    void refreshServerClosesCreatedClientWhenPersistenceFailsBeforeInstall() {
        McpServerConfigRepository serverRepository = mock(McpServerConfigRepository.class);
        McpClientFactory clientFactory = mock(McpClientFactory.class);
        McpSyncClient client = mock(McpSyncClient.class);
        McpServerConfigPo server = server();
        given(serverRepository.findByIdAndDeletedFalse(9L)).willReturn(Optional.of(server));
        given(clientFactory.create(server)).willReturn(client);
        given(serverRepository.save(any(McpServerConfigPo.class))).willThrow(new RuntimeException("save failed"));
        DynamicMcpClientManager manager = new DynamicMcpClientManager(serverRepository, clientFactory);

        assertThatThrownBy(() -> manager.refreshServer(9L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("save failed");
        verify(client).closeGracefully();
    }

    private McpServerConfigPo server() {
        McpServerConfigPo server = new McpServerConfigPo();
        server.setId(9L);
        server.setServerCode("docs");
        server.setServerName("Docs MCP");
        server.setTransportType(McpTransportType.STREAMABLE_HTTP);
        server.setBaseUrl("https://example.com");
        server.setEndpoint("/mcp");
        server.setEnabled(true);
        server.setStatus(McpServerStatus.CONNECTING);
        return server;
    }

}
