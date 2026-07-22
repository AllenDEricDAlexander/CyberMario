package top.egon.mario.agent.externalim;

import org.junit.jupiter.api.Test;
import top.egon.mario.agent.externalim.adapter.ExternalChatAdapterRegistry;
import top.egon.mario.agent.externalim.adapter.ExternalChatInboundAdapter;
import top.egon.mario.agent.externalim.adapter.ExternalChatReplyPort;
import top.egon.mario.agent.externalim.model.ExternalChatPlatform;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class ExternalChatContractTests {

    @Test
    void registryReturnsTheUniqueInboundAndReplyStrategies() {
        ExternalChatInboundAdapter inbound = mock(ExternalChatInboundAdapter.class);
        ExternalChatReplyPort reply = mock(ExternalChatReplyPort.class);
        given(inbound.platform()).willReturn(ExternalChatPlatform.TELEGRAM);
        given(reply.platform()).willReturn(ExternalChatPlatform.TELEGRAM);

        ExternalChatAdapterRegistry registry = new ExternalChatAdapterRegistry(List.of(inbound), List.of(reply));

        assertThat(registry.requireInbound(ExternalChatPlatform.TELEGRAM)).isSameAs(inbound);
        assertThat(registry.requireReply(ExternalChatPlatform.TELEGRAM)).isSameAs(reply);
    }

    @Test
    void registryRejectsDuplicateAndMissingStrategies() {
        ExternalChatInboundAdapter first = mock(ExternalChatInboundAdapter.class);
        ExternalChatInboundAdapter duplicate = mock(ExternalChatInboundAdapter.class);
        given(first.platform()).willReturn(ExternalChatPlatform.TELEGRAM);
        given(duplicate.platform()).willReturn(ExternalChatPlatform.TELEGRAM);

        assertThatThrownBy(() -> new ExternalChatAdapterRegistry(List.of(first, duplicate), List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate inbound adapter");

        ExternalChatAdapterRegistry empty = new ExternalChatAdapterRegistry(List.of(), List.of());
        assertThatThrownBy(() -> empty.requireReply(ExternalChatPlatform.QQ))
                .isInstanceOf(ExternalChatException.class)
                .hasMessageContaining("reply port is not configured");
    }
}
