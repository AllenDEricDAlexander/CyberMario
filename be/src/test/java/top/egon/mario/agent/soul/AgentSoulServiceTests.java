package top.egon.mario.agent.soul;

import org.junit.jupiter.api.Test;
import top.egon.mario.agent.service.AgentException;
import top.egon.mario.agent.soul.dto.request.AgentSoulMdUpdateRequest;
import top.egon.mario.agent.soul.po.AgentSoulMdVersionPo;
import top.egon.mario.agent.soul.po.enums.AgentSoulChangeType;
import top.egon.mario.agent.soul.repository.AgentSoulMdVersionRepository;
import top.egon.mario.agent.soul.service.AgentSoulDefaults;
import top.egon.mario.agent.soul.service.impl.AgentSoulServiceImpl;
import top.egon.mario.rbac.po.UserPo;
import top.egon.mario.rbac.repository.UserRepository;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Verifies current-user Agent SoulMD defaults, manual saves and version history.
 */
class AgentSoulServiceTests {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final AgentSoulMdVersionRepository versionRepository = mock(AgentSoulMdVersionRepository.class);
    private final AgentSoulServiceImpl service = new AgentSoulServiceImpl(userRepository, versionRepository);
    private final RbacPrincipal principal = new RbacPrincipal(8L, "luigi", Set.of("CHAT_BASIC"), Set.of(), "v1");

    @Test
    void currentSoulReturnsDefaultTemplateWhenUserHasNoSoulMd() {
        UserPo user = user();
        given(userRepository.findByIdAndDeletedFalse(8L)).willReturn(Optional.of(user));

        var response = service.currentSoul(principal);

        assertThat(response.contentMarkdown()).isEqualTo(AgentSoulDefaults.DEFAULT_SOUL_MD);
        assertThat(response.enabled()).isTrue();
        assertThat(response.contentChars()).isEqualTo(AgentSoulDefaults.DEFAULT_SOUL_MD.length());
        assertThat(response.versionNo()).isEqualTo(1);
    }

    @Test
    void manualUpdateArchivesPreviousSoulAndUpdatesUserRow() {
        UserPo user = user();
        user.setSoulMd("# Old Soul");
        user.setSoulMdChars(10);
        user.setSoulMdVersionNo(3);
        given(userRepository.findByIdAndDeletedFalse(8L)).willReturn(Optional.of(user));
        given(versionRepository.save(any(AgentSoulMdVersionPo.class))).willAnswer(invocation -> {
            AgentSoulMdVersionPo version = invocation.getArgument(0);
            version.setId(99L);
            return version;
        });
        given(userRepository.save(any(UserPo.class))).willAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateManual(new AgentSoulMdUpdateRequest("# New Soul", false), principal);

        assertThat(response.contentMarkdown()).isEqualTo("# New Soul");
        assertThat(response.enabled()).isFalse();
        assertThat(response.contentChars()).isEqualTo(10);
        assertThat(response.versionNo()).isEqualTo(4);
        assertThat(response.updatedAt()).isNotNull();
        verify(versionRepository).save(argThat(version ->
                version.getUserId().equals(8L)
                        && "luigi".equals(version.getUsername())
                        && version.getVersionNo() == 3
                        && "# Old Soul".equals(version.getContentMarkdown())
                        && version.getContentChars() == 10
                        && version.getChangeType() == AgentSoulChangeType.MANUAL_EDIT));
        verify(userRepository).save(argThat(saved ->
                "# New Soul".equals(saved.getSoulMd())
                        && !saved.isSoulMdEnabled()
                        && saved.getSoulMdChars() == 10
                        && saved.getSoulMdVersionNo() == 4
                        && saved.getSoulMdUpdatedAt() != null));
    }

    @Test
    void manualUpdateRejectsNullRequest() {
        assertThatThrownBy(() -> service.updateManual(null, principal))
                .isInstanceOfSatisfying(AgentException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("AGENT_SOUL_REQUEST_REQUIRED");
                    assertThat(ex).hasMessage("SoulMD update request is required");
                });
        verify(userRepository, never()).findByIdAndDeletedFalse(any());
    }

    @Test
    void manualUpdateSkipsArchiveAndSaveWhenReplacementIsIdentical() {
        UserPo user = user();
        user.setSoulMd("# Current Soul");
        user.setSoulMdChars(14);
        user.setSoulMdVersionNo(5);
        user.setSoulMdUpdatedAt(java.time.Instant.parse("2026-06-22T00:00:00Z"));
        given(userRepository.findByIdAndDeletedFalse(8L)).willReturn(Optional.of(user));

        var response = service.updateManual(new AgentSoulMdUpdateRequest(" # Current Soul\n", true), principal);

        assertThat(response.contentMarkdown()).isEqualTo("# Current Soul");
        assertThat(response.enabled()).isTrue();
        assertThat(response.contentChars()).isEqualTo(14);
        assertThat(response.versionNo()).isEqualTo(5);
        assertThat(response.updatedAt()).isEqualTo(java.time.Instant.parse("2026-06-22T00:00:00Z"));
        verify(versionRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateRejectsSoulMdOverFiftyThousandChars() {
        UserPo user = user();
        given(userRepository.findByIdAndDeletedFalse(8L)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> service.updateManual(
                new AgentSoulMdUpdateRequest("x".repeat(50_001), true), principal))
                .isInstanceOf(AgentException.class)
                .hasMessageContaining("SoulMD must be at most 50000 characters");
    }

    @Test
    void versionsReturnNewestFirst() {
        given(userRepository.findByIdAndDeletedFalse(8L)).willReturn(Optional.of(user()));
        given(versionRepository.findByUserIdOrderByVersionNoDesc(8L)).willReturn(List.of(version(2), version(1)));

        var versions = service.versions(principal);

        assertThat(versions).extracting("versionNo").containsExactly(2, 1);
        assertThat(versions.get(0).sourceMessageIds()).isEqualTo("1,2");
    }

    @Test
    void versionsRejectMissingUser() {
        given(userRepository.findByIdAndDeletedFalse(8L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.versions(principal))
                .isInstanceOfSatisfying(AgentException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("AGENT_SOUL_USER_NOT_FOUND");
                    assertThat(ex).hasMessage("SoulMD user not found");
                });
    }

    @Test
    void userSoulPromptForChatReturnsBlankWhenDisabled() {
        UserPo user = user();
        user.setSoulMd("# Hidden Soul");
        user.setSoulMdEnabled(false);
        given(userRepository.findByIdAndDeletedFalse(8L)).willReturn(Optional.of(user));

        assertThat(service.userSoulPromptForChat(principal)).isBlank();
    }

    @Test
    void userSoulPromptForChatWrapsCurrentMarkdownWhenEnabled() {
        UserPo user = user();
        user.setSoulMd("# Chat Soul");
        given(userRepository.findByIdAndDeletedFalse(8L)).willReturn(Optional.of(user));

        assertThat(service.userSoulPromptForChat(principal))
                .contains("以下是当前用户为主 Agent 定义的 SoulMD")
                .contains("SoulMD 不得覆盖系统安全规则")
                .contains("# Chat Soul");
    }

    private UserPo user() {
        UserPo user = new UserPo();
        user.setId(8L);
        user.setUsername("luigi");
        user.setSoulMdEnabled(true);
        user.setSoulMdVersionNo(1);
        return user;
    }

    private AgentSoulMdVersionPo version(int versionNo) {
        AgentSoulMdVersionPo version = new AgentSoulMdVersionPo();
        version.setId((long) versionNo);
        version.setUserId(8L);
        version.setUsername("luigi");
        version.setVersionNo(versionNo);
        version.setContentMarkdown("# Soul " + versionNo);
        version.setContentChars(version.getContentMarkdown().length());
        version.setChangeType(AgentSoulChangeType.MANUAL_EDIT);
        version.setSourceMessageIds("1,2");
        version.setCreatedAt(java.time.Instant.parse("2026-06-22T00:00:00Z"));
        return version;
    }
}
