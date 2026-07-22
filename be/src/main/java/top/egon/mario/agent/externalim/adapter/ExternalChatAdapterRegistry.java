package top.egon.mario.agent.externalim.adapter;

import org.springframework.stereotype.Component;
import top.egon.mario.agent.externalim.ExternalChatException;
import top.egon.mario.agent.externalim.model.ExternalChatPlatform;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Component
public class ExternalChatAdapterRegistry {

    private final Map<ExternalChatPlatform, ExternalChatInboundAdapter> inbound;
    private final Map<ExternalChatPlatform, ExternalChatReplyPort> reply;

    public ExternalChatAdapterRegistry(List<ExternalChatInboundAdapter> inbound,
                                       List<ExternalChatReplyPort> reply) {
        this.inbound = unique(inbound, ExternalChatInboundAdapter::platform, "inbound adapter");
        this.reply = unique(reply, ExternalChatReplyPort::platform, "reply port");
    }

    public ExternalChatInboundAdapter requireInbound(ExternalChatPlatform platform) {
        ExternalChatInboundAdapter adapter = inbound.get(platform);
        if (adapter == null) {
            throw new ExternalChatException("EXTERNAL_CHAT_ADAPTER_NOT_CONFIGURED",
                    "inbound adapter is not configured for " + platform);
        }
        return adapter;
    }

    public ExternalChatReplyPort requireReply(ExternalChatPlatform platform) {
        ExternalChatReplyPort port = reply.get(platform);
        if (port == null) {
            throw new ExternalChatException("EXTERNAL_CHAT_REPLY_NOT_CONFIGURED",
                    "reply port is not configured for " + platform);
        }
        return port;
    }

    private <T> Map<ExternalChatPlatform, T> unique(List<T> values,
                                                    Function<T, ExternalChatPlatform> platform,
                                                    String label) {
        Map<ExternalChatPlatform, T> result = new EnumMap<>(ExternalChatPlatform.class);
        for (T value : values == null ? List.<T>of() : values) {
            ExternalChatPlatform key = platform.apply(value);
            if (key == null || result.putIfAbsent(key, value) != null) {
                throw new IllegalStateException("duplicate " + label + " for " + key);
            }
        }
        return Map.copyOf(result);
    }
}
