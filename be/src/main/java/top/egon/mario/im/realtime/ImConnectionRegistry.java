package top.egon.mario.im.realtime;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Component
public class ImConnectionRegistry {

    private final Map<Long, CopyOnWriteArrayList<Consumer<Map<String, Object>>>> connections = new ConcurrentHashMap<>();

    public Registration register(Long userId, Consumer<Map<String, Object>> sink) {
        if (userId == null || sink == null) {
            throw new IllegalArgumentException("userId and sink are required");
        }
        connections.computeIfAbsent(userId, ignored -> new CopyOnWriteArrayList<>()).add(sink);
        return () -> unregister(userId, sink);
    }

    public int deliverToUser(Long userId, Map<String, Object> frame) {
        List<Consumer<Map<String, Object>>> sinks = connections.get(userId);
        if (sinks == null || sinks.isEmpty()) {
            return 0;
        }
        sinks.forEach(sink -> sink.accept(frame));
        return sinks.size();
    }

    private void unregister(Long userId, Consumer<Map<String, Object>> sink) {
        List<Consumer<Map<String, Object>>> sinks = connections.get(userId);
        if (sinks == null) {
            return;
        }
        sinks.remove(sink);
        if (sinks.isEmpty()) {
            connections.remove(userId, sinks);
        }
    }

    public interface Registration extends AutoCloseable {

        @Override
        void close();
    }
}
