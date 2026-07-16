package top.egon.mario.im.platform;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.common.utils.LogUtil;
import top.egon.mario.rbac.po.RolePo;
import top.egon.mario.rbac.po.UserPo;
import top.egon.mario.rbac.po.UserRolePo;
import top.egon.mario.rbac.po.enums.RbacStatus;
import top.egon.mario.rbac.repository.RoleRepository;
import top.egon.mario.rbac.repository.UserRepository;
import top.egon.mario.rbac.repository.UserRoleRepository;
import top.egon.mario.rbac.service.model.RbacPermissionChangedEvent;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Idempotently grants IM_USER to enabled accounts created before platform IM was introduced.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 8)
@Slf4j
public class ImUserRoleBackfill implements ApplicationRunner {

    private static final String IM_USER_ROLE_CODE = "IM_USER";
    private static final int BATCH_SIZE = 200;

    private final ImUserRoleBackfillProperties properties;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ImUserRoleBackfill(ImUserRoleBackfillProperties properties,
                              UserRepository userRepository,
                              RoleRepository roleRepository,
                              UserRoleRepository userRoleRepository,
                              ApplicationEventPublisher eventPublisher) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void run(ApplicationArguments args) {
        backfill();
    }

    @Transactional
    public int backfill() {
        if (!properties.enabled()) {
            LogUtil.debug(log).log("platform IM user role backfill skipped, enabled=false");
            return 0;
        }
        RolePo role = roleRepository.findByRoleCodeAndDeletedFalse(IM_USER_ROLE_CODE)
                .orElseThrow(() -> new IllegalStateException("Platform IM role does not exist: " + IM_USER_ROLE_CODE));
        if (role.getStatus() != RbacStatus.ENABLED) {
            throw new IllegalStateException("Platform IM role is disabled: " + IM_USER_ROLE_CODE);
        }

        int pageNumber = 0;
        int grantCount = 0;
        Page<UserPo> page;
        do {
            page = userRepository.findByStatusAndDeletedFalse(
                    RbacStatus.ENABLED,
                    PageRequest.of(pageNumber++, BATCH_SIZE, Sort.by(Sort.Direction.ASC, "id")));
            grantCount += grantMissing(role.getId(), page.getContent());
        } while (page.hasNext());

        if (grantCount > 0) {
            eventPublisher.publishEvent(new RbacPermissionChangedEvent("backfill IM_USER role grants"));
        }
        LogUtil.info(log).log("platform IM user role backfill completed, grantCount={}", grantCount);
        return grantCount;
    }

    private int grantMissing(Long roleId, List<UserPo> users) {
        if (users.isEmpty()) {
            return 0;
        }
        List<Long> userIds = users.stream().map(UserPo::getId).toList();
        Set<Long> assignedUserIds = userRoleRepository.findByRoleIdAndUserIdIn(roleId, userIds).stream()
                .map(UserRolePo::getUserId)
                .collect(Collectors.toSet());
        Instant now = Instant.now();
        List<UserRolePo> grants = userIds.stream()
                .filter(userId -> !assignedUserIds.contains(userId))
                .map(userId -> grant(userId, roleId, now))
                .toList();
        userRoleRepository.saveAll(grants);
        return grants.size();
    }

    private UserRolePo grant(Long userId, Long roleId, Instant grantedAt) {
        UserRolePo grant = new UserRolePo();
        grant.setUserId(userId);
        grant.setRoleId(roleId);
        grant.setGrantedAt(grantedAt);
        return grant;
    }
}
