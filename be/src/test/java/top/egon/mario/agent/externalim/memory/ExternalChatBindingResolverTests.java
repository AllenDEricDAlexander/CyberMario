package top.egon.mario.agent.externalim.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.egon.mario.agent.externalim.ExternalChatException;
import top.egon.mario.agent.externalim.memory.impl.DefaultExternalChatBindingResolver;
import top.egon.mario.agent.externalim.memory.model.ResolvedExternalChatBinding;
import top.egon.mario.agent.externalim.memory.po.AgentMemorySpacePo;
import top.egon.mario.agent.externalim.memory.po.ExternalChatBindingPo;
import top.egon.mario.agent.externalim.memory.po.enums.AgentMemorySpaceStatus;
import top.egon.mario.agent.externalim.memory.repository.AgentMemorySpaceRepository;
import top.egon.mario.agent.externalim.memory.repository.ExternalChatBindingRepository;
import top.egon.mario.agent.externalim.model.ExternalChatMessage;
import top.egon.mario.agent.externalim.model.ExternalChatPlatform;
import top.egon.mario.agent.externalim.model.ExternalConversationType;
import top.egon.mario.agent.externalim.model.ExternalMessageType;
import top.egon.mario.agent.externalim.model.ExternalSender;
import top.egon.mario.agent.externalim.model.ExternalSenderType;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ExternalChatBindingResolverTests {

    @Mock
    private ExternalChatBindingRepository bindingRepository;
    @Mock
    private AgentMemorySpaceRepository spaceRepository;

    private ExternalChatBindingResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new DefaultExternalChatBindingResolver(bindingRepository, spaceRepository);
    }

    @Test
    void resolverRejectsAWebhookConversationTypeThatDiffersFromTheBinding() {
        ExternalChatBindingPo binding = binding(ExternalConversationType.DIRECT);
        given(bindingRepository.findByPlatformAndConnectorIdAndExternalConversationIdAndEnabledTrueAndDeletedFalse(
                ExternalChatPlatform.TELEGRAM, "main", "-1001")).willReturn(Optional.of(binding));

        assertThatThrownBy(() -> resolver.resolve(message(ExternalConversationType.GROUP)))
                .isInstanceOf(ExternalChatException.class)
                .extracting(error -> ((ExternalChatException) error).code())
                .isEqualTo("EXTERNAL_CHAT_BINDING_TYPE_MISMATCH");
    }

    @Test
    void resolverGetsOwnerOnlyFromTheBoundSpace() {
        given(bindingRepository.findByPlatformAndConnectorIdAndExternalConversationIdAndEnabledTrueAndDeletedFalse(
                ExternalChatPlatform.TELEGRAM, "main", "-1001"))
                .willReturn(Optional.of(binding(ExternalConversationType.GROUP)));
        given(spaceRepository.findBySpaceIdAndDeletedFalse("space-1")).willReturn(Optional.of(activeSpace()));

        ResolvedExternalChatBinding resolved = resolver.resolve(message(ExternalConversationType.GROUP));

        assertThat(resolved.ownerUserId()).isEqualTo(8L);
        assertThat(resolved.memorySpaceId()).isEqualTo("space-1");
    }

    private ExternalChatBindingPo binding(ExternalConversationType type) {
        ExternalChatBindingPo binding = new ExternalChatBindingPo();
        binding.setSpaceId("space-1");
        binding.setPlatform(ExternalChatPlatform.TELEGRAM);
        binding.setConnectorId("main");
        binding.setExternalConversationId("-1001");
        binding.setConversationType(type);
        binding.setAudienceKey("telegram:main:-1001");
        binding.setEnabled(true);
        return binding;
    }

    private AgentMemorySpacePo activeSpace() {
        AgentMemorySpacePo space = new AgentMemorySpacePo();
        space.setSpaceId("space-1");
        space.setOwnerUserId(8L);
        space.setName("Family assistant");
        space.setStatus(AgentMemorySpaceStatus.ACTIVE);
        return space;
    }

    private ExternalChatMessage message(ExternalConversationType type) {
        return new ExternalChatMessage("update-1", "message-1", ExternalChatPlatform.TELEGRAM,
                "main", "-1001", type, "telegram:main:-1001",
                new ExternalSender("user-9", "Alice", ExternalSenderType.HUMAN),
                ExternalMessageType.TEXT, "hello", false, false, Instant.now());
    }
}
