package top.egon.mario.agent.externalim.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.egon.mario.agent.externalim.ExternalChatException;
import top.egon.mario.agent.externalim.memory.impl.DefaultAgentMemorySpaceService;
import top.egon.mario.agent.externalim.memory.model.ExternalChatBindingCommand;
import top.egon.mario.agent.externalim.memory.po.AgentMemorySpacePo;
import top.egon.mario.agent.externalim.memory.po.enums.AgentMemorySpaceStatus;
import top.egon.mario.agent.externalim.memory.repository.AgentMemorySpaceRepository;
import top.egon.mario.agent.externalim.memory.repository.ExternalChatBindingRepository;
import top.egon.mario.agent.externalim.model.ExternalChatPlatform;
import top.egon.mario.agent.externalim.model.ExternalConversationType;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AgentMemorySpaceServiceTests {

    @Mock
    private AgentMemorySpaceRepository spaceRepository;
    @Mock
    private ExternalChatBindingRepository bindingRepository;

    private AgentMemorySpaceService service;
    private RbacPrincipal principal;

    @BeforeEach
    void setUp() {
        service = new DefaultAgentMemorySpaceService(spaceRepository, bindingRepository);
        principal = new RbacPrincipal(8L, "luigi", Set.of(), Set.of(), "v1");
    }

    @Test
    void createsAnActiveSpaceForTheAuthenticatedOwner() {
        given(spaceRepository.save(any(AgentMemorySpacePo.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        AgentMemorySpacePo saved = service.create("Family assistant", principal);

        assertThat(saved.getSpaceId()).isNotBlank();
        assertThat(saved.getOwnerUserId()).isEqualTo(8L);
        assertThat(saved.getName()).isEqualTo("Family assistant");
        assertThat(saved.getStatus()).isEqualTo(AgentMemorySpaceStatus.ACTIVE);
    }

    @Test
    void bindingRequiresASpaceOwnedByTheCurrentUser() {
        given(spaceRepository.findBySpaceIdAndOwnerUserIdAndDeletedFalse("space-1", 8L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.bind(new ExternalChatBindingCommand(
                "space-1", ExternalChatPlatform.TELEGRAM, "main", "-1001",
                ExternalConversationType.GROUP, "telegram:main:-1001"), principal))
                .isInstanceOf(ExternalChatException.class)
                .hasMessageContaining("memory space not found");
    }
}
