package top.egon.mario.rbac.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import top.egon.mario.rbac.dto.enums.ActivationStatus;
import top.egon.mario.rbac.dto.response.UserResponse;
import top.egon.mario.rbac.po.UserPo;
import top.egon.mario.rbac.po.enums.RbacStatus;
import top.egon.mario.rbac.repository.UserRepository;
import top.egon.mario.rbac.service.model.UserQuery;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers database-side filtering for the admin user list.
 *
 * <p>The console used to filter the already-loaded page in the browser, which
 * silently hid matches on other pages. These tests pin the server-side
 * behaviour that replaced it.
 */
@SpringBootTest
class RbacUserQueryTests {

    private static final String PREFIX = "userquerytest-";

    @Autowired
    private RbacUserService userService;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private ChatModel chatModel;

    @BeforeEach
    void seedUsers() {
        removeFixtures();
        userRepository.saveAll(List.of(
                fixture("alpha", "Alpha Person", "alpha@example.com", "13800000001",
                        RbacStatus.ENABLED, Instant.now()),
                fixture("beta", "Beta Person", "beta@example.com", "13800000002",
                        RbacStatus.DISABLED, Instant.now()),
                fixture("gamma", "Gamma Person", "gamma@example.com", "13800000003",
                        RbacStatus.ENABLED, null)));
    }

    @AfterEach
    void cleanUp() {
        removeFixtures();
    }

    @Test
    void keywordMatchesAcrossIdentityColumnsCaseInsensitively() {
        assertThat(accountNos(new UserQuery(PREFIX + "ALPHA", null, null)))
                .containsExactly(PREFIX + "alpha");
        // Nickname, email and mobile are all part of the same OR group.
        assertThat(accountNos(new UserQuery("Beta Person", null, null)))
                .containsExactly(PREFIX + "beta");
        assertThat(accountNos(new UserQuery("gamma@example.com", null, null)))
                .containsExactly(PREFIX + "gamma");
        assertThat(accountNos(new UserQuery("13800000002", null, null)))
                .containsExactly(PREFIX + "beta");
    }

    @Test
    void blankKeywordDoesNotRestrictResults() {
        assertThat(accountNos(new UserQuery("   ", null, null)))
                .containsExactlyInAnyOrder(PREFIX + "alpha", PREFIX + "beta", PREFIX + "gamma");
    }

    @Test
    void statusAndActivationFiltersCombineWithTheKeyword() {
        assertThat(accountNos(new UserQuery(PREFIX, RbacStatus.DISABLED, null)))
                .containsExactly(PREFIX + "beta");
        assertThat(accountNos(new UserQuery(PREFIX, null, ActivationStatus.PENDING_ACTIVATION)))
                .containsExactly(PREFIX + "gamma");
        assertThat(accountNos(new UserQuery(PREFIX, null, ActivationStatus.ACTIVATED)))
                .containsExactlyInAnyOrder(PREFIX + "alpha", PREFIX + "beta");
        assertThat(accountNos(new UserQuery(PREFIX, RbacStatus.ENABLED, ActivationStatus.ACTIVATED)))
                .containsExactly(PREFIX + "alpha");
    }

    @Test
    void aNonMatchingKeywordReturnsAnEmptyPageRatherThanEveryUser() {
        assertThat(accountNos(new UserQuery(PREFIX + "no-such-account", null, null))).isEmpty();
    }

    @Test
    void theUnfilteredOverloadStillReturnsEveryUser() {
        List<String> all = userService.getUserPage(PageRequest.of(0, 500, Sort.by("id").descending()))
                .getContent().stream()
                .map(UserResponse::getAccountNo)
                .filter(accountNo -> accountNo.startsWith(PREFIX))
                .toList();

        assertThat(all).containsExactlyInAnyOrder(PREFIX + "alpha", PREFIX + "beta", PREFIX + "gamma");
    }

    private List<String> accountNos(UserQuery userQuery) {
        return userService.getUserPage(userQuery, PageRequest.of(0, 500, Sort.by("id").descending()))
                .getContent().stream()
                .map(UserResponse::getAccountNo)
                .filter(accountNo -> accountNo.startsWith(PREFIX))
                .toList();
    }

    private UserPo fixture(String suffix, String nickname, String email, String mobile,
                           RbacStatus status, Instant activatedAt) {
        UserPo user = new UserPo();
        user.setAccountNo(PREFIX + suffix);
        user.setUsername(PREFIX + suffix);
        user.setNickname(nickname);
        user.setEmail(email);
        user.setMobile(mobile);
        user.setPasswordHash("not-a-real-hash");
        user.setStatus(status);
        user.setActivatedAt(activatedAt);
        return user;
    }

    private void removeFixtures() {
        List<UserPo> existing = userRepository.findAll().stream()
                .filter(user -> user.getAccountNo() != null && user.getAccountNo().startsWith(PREFIX))
                .toList();
        userRepository.deleteAll(existing);
    }
}
