package top.egon.mario.rbac.application;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import top.egon.mario.rbac.dto.response.UserDirectoryItemResponse;
import top.egon.mario.rbac.po.UserPo;
import top.egon.mario.rbac.po.enums.RbacStatus;
import top.egon.mario.rbac.repository.UserRepository;
import top.egon.mario.rbac.service.RbacException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RbacUserDirectoryFacadeTests {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final RbacUserDirectoryFacade facade = new RbacUserDirectoryFacade(userRepository);

    @Test
    void searchCapsPageAndReturnsOnlySafeProjection() {
        UserPo user = user(2L, "U0002", "private-login", "Luigi", false, RbacStatus.ENABLED);
        user.setEmail("private@example.com");
        user.setMobile("13900000000");
        user.setPasswordHash("private-hash");
        when(userRepository.searchDirectory(eq("Luigi"), eq(1L), eq(RbacStatus.ENABLED), any()))
                .thenReturn(new PageImpl<>(List.of(user)));

        UserDirectoryItemResponse response = facade.search(" Luigi ", 1L, 0, 200).getContent().getFirst();

        assertThat(response.userId()).isEqualTo(2L);
        assertThat(response.accountNo()).isEqualTo("U0002");
        assertThat(response.displayName()).isEqualTo("Luigi");
        assertThat(response.avatarUrl()).isEqualTo("https://example.com/2.png");
        assertThat(UserDirectoryItemResponse.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("userId", "accountNo", "displayName", "avatarUrl");

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).searchDirectory(eq("Luigi"), eq(1L), eq(RbacStatus.ENABLED), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void searchRejectsBlankKeywordBeforeRepositoryAccess() {
        assertThatThrownBy(() -> facade.search(" ", 1L, 0, 20))
                .isInstanceOf(RbacException.class)
                .extracting("code")
                .isEqualTo("RBAC_USER_DIRECTORY_KEYWORD_REQUIRED");
    }

    @Test
    void batchLookupExcludesDisabledAndLockedUsersAndFallsBackToAccountNumber() {
        UserPo available = user(2L, "U0002", "luigi", null, false, RbacStatus.ENABLED);
        UserPo disabled = user(3L, "U0003", "toad", "Toad", false, RbacStatus.DISABLED);
        UserPo locked = user(4L, "U0004", "peach", "Peach", true, RbacStatus.ENABLED);
        when(userRepository.findByIdInAndDeletedFalse(List.of(2L, 3L, 4L)))
                .thenReturn(List.of(available, disabled, locked));

        Map<Long, UserDirectoryItemResponse> users = facade.findEnabledByIds(List.of(2L, 3L, 4L));

        assertThat(users).containsOnlyKeys(2L);
        assertThat(users.get(2L).displayName()).isEqualTo("U0002");
    }

    private UserPo user(Long id, String accountNo, String username, String nickname,
                        boolean locked, RbacStatus status) {
        UserPo user = new UserPo();
        user.setId(id);
        user.setAccountNo(accountNo);
        user.setUsername(username);
        user.setNickname(nickname);
        user.setAvatarUrl("https://example.com/" + id + ".png");
        user.setPasswordHash("hash");
        user.setLocked(locked);
        user.setStatus(status);
        return user;
    }
}
