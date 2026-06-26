package top.egon.mario.agent.mcp.runtime;

import com.google.common.util.concurrent.Striped;
import io.modelcontextprotocol.client.McpSyncClient;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.agent.mcp.po.McpServerConfigPo;
import top.egon.mario.agent.mcp.po.enums.McpServerStatus;
import top.egon.mario.agent.mcp.repository.McpServerConfigRepository;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;

/**
 * Maintains initialized runtime MCP clients for enabled servers.
 */
@Component
@RequiredArgsConstructor
public class DynamicMcpClientManager {

    private static final int ERROR_LIMIT = 1024;

    private final McpServerConfigRepository serverRepository;
    private final McpClientFactory clientFactory;
    private final Map<Long, McpSyncClient> clients = new ConcurrentHashMap<>();
    private final Striped<Lock> serverLifecycleLocks = Striped.lazyWeakLock(64);

    @Transactional
    public void reloadEnabledServers() {
        for (McpServerConfigPo server : serverRepository.findByEnabledTrueAndDeletedFalseOrderByIdAsc()) {
            refreshServer(server.getId());
        }
    }

    @Transactional
    public void refreshServer(Long serverId) {
        Lock lock = serverLifecycleLocks.get(serverId);
        lock.lock();
        try {
            doRefreshServer(serverId);
        } finally {
            lock.unlock();
        }
    }

    private void doRefreshServer(Long serverId) {
        Optional<McpServerConfigPo> serverOptional = serverRepository.findByIdAndDeletedFalse(serverId);
        closeClientQuietly(clients.remove(serverId));
        if (serverOptional.isEmpty()) {
            return;
        }

        McpServerConfigPo server = serverOptional.get();
        if (!server.isEnabled()) {
            server.setStatus(McpServerStatus.DISABLED);
            serverRepository.save(server);
            return;
        }

        McpSyncClient newClient = null;
        boolean installed = false;
        try {
            newClient = clientFactory.create(server);
            server.setStatus(McpServerStatus.CONNECTED);
            server.setLastError(null);
            server.setLastConnectedAt(Instant.now());
            serverRepository.save(server);
            clients.put(serverId, newClient);
            installed = true;
        } catch (RuntimeException e) {
            server.setStatus(McpServerStatus.FAILED);
            server.setLastError(limit(e.getMessage(), ERROR_LIMIT));
            serverRepository.save(server);
            if (newClient != null) {
                throw e;
            }
        } finally {
            if (!installed) {
                closeClientQuietly(newClient);
            }
        }
    }

    @Transactional
    public void disableServer(Long serverId) {
        Lock lock = serverLifecycleLocks.get(serverId);
        lock.lock();
        try {
            closeClientQuietly(clients.remove(serverId));
            serverRepository.findByIdAndDeletedFalse(serverId).ifPresent(server -> {
                server.setEnabled(false);
                server.setStatus(McpServerStatus.DISABLED);
                serverRepository.save(server);
            });
        } finally {
            lock.unlock();
        }
    }

    public Optional<McpSyncClient> client(Long serverId) {
        return Optional.ofNullable(clients.get(serverId));
    }

    public Map<Long, McpSyncClient> clients() {
        return Map.copyOf(clients);
    }

    /**
     * Closes dynamic clients before Spring tears down Reactor resources.
     */
    @PreDestroy
    public void shutdown() {
        for (Map.Entry<Long, McpSyncClient> entry : clients.entrySet()) {
            if (clients.remove(entry.getKey(), entry.getValue())) {
                closeClientQuietly(entry.getValue());
            }
        }
    }

    private void closeClient(McpSyncClient client) {
        if (client == null) {
            return;
        }
        try {
            if (!client.closeGracefully()) {
                client.close();
            }
        } catch (RuntimeException e) {
            client.close();
        }
    }

    private void closeClientQuietly(McpSyncClient client) {
        try {
            closeClient(client);
        } catch (RuntimeException ignored) {
            // best effort
        }
    }

    private String limit(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit);
    }

}
