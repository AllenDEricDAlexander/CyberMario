package top.egon.mario.agent.memory;

import org.junit.jupiter.api.Test;
import top.egon.mario.agent.memory.po.AgentLongTermMemoryPo;
import top.egon.mario.agent.memory.po.AgentLongTermMemoryVersionPo;
import top.egon.mario.agent.memory.po.enums.AgentLongTermMemoryScopeType;
import top.egon.mario.agent.memory.repository.AgentLongTermMemoryRepository;
import top.egon.mario.agent.memory.repository.AgentLongTermMemoryVersionRepository;
import top.egon.mario.agent.memory.service.impl.AgentLongTermMemoryServiceImpl;
import top.egon.mario.agent.memory.service.model.AgentLongTermMemoryMergeRequest;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Verifies user-scoped Markdown long-term memory persistence rules.
 */
class AgentLongTermMemoryServiceTests {

    private final AgentLongTermMemoryRepository memoryRepository = mock(AgentLongTermMemoryRepository.class);
    private final AgentLongTermMemoryVersionRepository versionRepository = mock(AgentLongTermMemoryVersionRepository.class);
    private final AgentLongTermMemoryServiceImpl service =
            new AgentLongTermMemoryServiceImpl(memoryRepository, versionRepository);
    private final RbacPrincipal principal = new RbacPrincipal(8L, "luigi", Set.of("CHAT_BASIC"), Set.of(), "v1");

    @Test
    void createsDefaultUserAgentMemoryWhenMissing() {
        given(memoryRepository.findByUserIdAndScopeTypeAndScopeKeyAndDeletedFalse(
                8L, AgentLongTermMemoryScopeType.USER_AGENT, "__web_private__"))
                .willReturn(Optional.empty());
        given(memoryRepository.save(any(AgentLongTermMemoryPo.class))).willAnswer(invocation -> {
            AgentLongTermMemoryPo po = invocation.getArgument(0);
            po.setId(100L);
            return po;
        });
        given(versionRepository.save(any(AgentLongTermMemoryVersionPo.class))).willAnswer(invocation -> {
            AgentLongTermMemoryVersionPo po = invocation.getArgument(0);
            po.setId(200L);
            return po;
        });

        AgentLongTermMemoryPo memory = service.getOrCreateUserAgentMemory(principal);

        assertThat(memory.getUserId()).isEqualTo(8L);
        assertThat(memory.getUsername()).isEqualTo("luigi");
        assertThat(memory.getScopeType()).isEqualTo(AgentLongTermMemoryScopeType.USER_AGENT);
        assertThat(memory.getContentMarkdown()).contains("# User Memory");
        assertThat(memory.getContentChars()).isEqualTo(memory.getContentMarkdown().length());
        assertThat(memory.getActiveVersionId()).isEqualTo(200L);
    }

    @Test
    void rejectsMergedMarkdownOverTwentyThousandCharacters() {
        String tooLong = "a".repeat(20_001);

        assertThatThrownBy(() -> service.merge(new AgentLongTermMemoryMergeRequest(
                8L, "luigi", AgentLongTermMemoryScopeType.USER_AGENT, tooLong,
                "summary", "session-1", "1,2", "request-1", "trace-1")))
                .hasMessageContaining("long-term memory exceeds");
    }

    @Test
    void mergeCreatesNewVersionAndActivatesIt() {
        AgentLongTermMemoryPo memory = new AgentLongTermMemoryPo();
        memory.setId(100L);
        memory.setUserId(8L);
        memory.setScopeType(AgentLongTermMemoryScopeType.USER_AGENT);
        memory.setContentMarkdown("# User Memory");
        memory.setContentChars(memory.getContentMarkdown().length());
        given(memoryRepository.findByUserIdAndScopeTypeAndScopeKeyAndDeletedFalse(
                8L, AgentLongTermMemoryScopeType.USER_AGENT, "__web_private__"))
                .willReturn(Optional.of(memory));
        given(memoryRepository.save(any(AgentLongTermMemoryPo.class))).willAnswer(invocation -> invocation.getArgument(0));
        AgentLongTermMemoryVersionPo existingVersion = new AgentLongTermMemoryVersionPo();
        existingVersion.setVersionNo(3);
        given(versionRepository.findByMemoryIdOrderByVersionNoDesc(100L)).willReturn(List.of(existingVersion));
        given(versionRepository.save(any(AgentLongTermMemoryVersionPo.class))).willAnswer(invocation -> {
            AgentLongTermMemoryVersionPo version = invocation.getArgument(0);
            version.setId(300L);
            return version;
        });

        AgentLongTermMemoryPo merged = service.merge(new AgentLongTermMemoryMergeRequest(
                8L, "luigi", AgentLongTermMemoryScopeType.USER_AGENT, "# User Memory\n\n## Preferences\n- 中文回答",
                "add preference", "session-1", "1,2", "request-1", "trace-1"));

        assertThat(merged.getActiveVersionId()).isEqualTo(300L);
        assertThat(merged.getContentMarkdown()).contains("中文回答");
        verify(versionRepository).save(any(AgentLongTermMemoryVersionPo.class));
    }

    @Test
    void imSharedMemoryUsesTheSpaceAsItsPortableUniqueScopeKey() {
        given(memoryRepository.findByUserIdAndScopeTypeAndScopeKeyAndDeletedFalse(
                8L, AgentLongTermMemoryScopeType.IM_SHARED, "space-1")).willReturn(Optional.empty());
        given(memoryRepository.save(any(AgentLongTermMemoryPo.class)))
                .willAnswer(invocation -> {
                    AgentLongTermMemoryPo value = invocation.getArgument(0);
                    value.setId(7L);
                    return value;
                });
        given(versionRepository.findByMemoryIdOrderByVersionNoDesc(7L)).willReturn(List.of());
        given(versionRepository.save(any())).willAnswer(invocation -> {
            AgentLongTermMemoryVersionPo value = invocation.getArgument(0);
            value.setId(11L);
            return value;
        });

        AgentLongTermMemoryPo memory = service.getOrCreate(
                8L, null, AgentLongTermMemoryScopeType.IM_SHARED, "space-1");

        assertThat(memory.getMemorySpaceId()).isEqualTo("space-1");
        assertThat(memory.getScopeKey()).isEqualTo("space-1");
    }
}
