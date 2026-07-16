package top.egon.mario.im;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.im.platform.ImUserRoleBackfill;
import top.egon.mario.im.platform.ImUserRoleBackfillProperties;
import top.egon.mario.rbac.po.RolePo;
import top.egon.mario.rbac.po.UserPo;
import top.egon.mario.rbac.po.UserRolePo;
import top.egon.mario.rbac.po.enums.RbacStatus;
import top.egon.mario.rbac.repository.RoleRepository;
import top.egon.mario.rbac.repository.UserRepository;
import top.egon.mario.rbac.repository.UserRoleRepository;
import top.egon.mario.rbac.service.model.RbacPermissionChangedEvent;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
@RecordApplicationEvents
@Transactional
class ImUserRoleBackfillTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private ApplicationEvents applicationEvents;

    @Test
    void disabledBackfillPerformsNoGrantAndDoesNotRequireRole() {
        UserPo user = user("im-backfill-disabled-runner", RbacStatus.ENABLED, false);

        assertThat(backfill(false).backfill()).isZero();
        assertThat(userRoleRepository.findByUserId(user.getId())).isEmpty();
        assertThat(permissionEvents()).isZero();
    }

    @Test
    void enabledBackfillFailsClearlyWhenRoleIsMissingOrDisabled() {
        assertThatThrownBy(() -> backfill(true).backfill())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Platform IM role does not exist: IM_USER");

        RolePo role = role("IM_USER", RbacStatus.DISABLED);
        assertThatThrownBy(() -> backfill(true).backfill())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Platform IM role is disabled: IM_USER");
        assertThat(userRoleRepository.findByRoleId(role.getId())).isEmpty();
    }

    @Test
    void backfillGrantsOnlyMissingEnabledUsersAndPublishesOnlyForChanges() {
        long baselineEnabledUsers = userRepository.findByStatusAndDeletedFalse(
                RbacStatus.ENABLED, PageRequest.of(0, 1)).getTotalElements();
        RolePo role = role("IM_USER", RbacStatus.ENABLED);
        UserPo newUser = user("im-backfill-new", RbacStatus.ENABLED, false);
        UserPo existingUser = user("im-backfill-existing", RbacStatus.ENABLED, false);
        UserPo disabledUser = user("im-backfill-disabled", RbacStatus.DISABLED, false);
        UserPo deletedUser = user("im-backfill-deleted", RbacStatus.ENABLED, true);
        grant(existingUser.getId(), role.getId());

        assertThat(backfill(true).backfill()).isEqualTo(baselineEnabledUsers + 1);
        assertThat(userRoleRepository.findByRoleId(role.getId()))
                .extracting(UserRolePo::getUserId)
                .contains(newUser.getId(), existingUser.getId())
                .doesNotContain(disabledUser.getId(), deletedUser.getId());
        assertThat(permissionEvents()).isEqualTo(1);

        assertThat(backfill(true).backfill()).isZero();
        assertThat(userRoleRepository.findByRoleId(role.getId())).hasSize((int) baselineEnabledUsers + 2);
        assertThat(permissionEvents()).isEqualTo(1);
    }

    private ImUserRoleBackfill backfill(boolean enabled) {
        return new ImUserRoleBackfill(
                new ImUserRoleBackfillProperties(enabled),
                userRepository,
                roleRepository,
                userRoleRepository,
                eventPublisher
        );
    }

    private UserPo user(String accountNo, RbacStatus status, boolean deleted) {
        UserPo user = new UserPo();
        user.setAccountNo(accountNo);
        user.setUsername(accountNo);
        user.setPasswordHash("test-password-hash");
        user.setStatus(status);
        user.setDeleted(deleted);
        return userRepository.saveAndFlush(user);
    }

    private RolePo role(String roleCode, RbacStatus status) {
        RolePo role = new RolePo();
        role.setRoleCode(roleCode);
        role.setRoleName(roleCode);
        role.setStatus(status);
        return roleRepository.saveAndFlush(role);
    }

    private void grant(Long userId, Long roleId) {
        UserRolePo grant = new UserRolePo();
        grant.setUserId(userId);
        grant.setRoleId(roleId);
        grant.setGrantedAt(Instant.now());
        userRoleRepository.saveAndFlush(grant);
    }

    private long permissionEvents() {
        return applicationEvents.stream(RbacPermissionChangedEvent.class).count();
    }
}
