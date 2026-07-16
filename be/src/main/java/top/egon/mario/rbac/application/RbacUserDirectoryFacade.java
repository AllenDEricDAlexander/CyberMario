package top.egon.mario.rbac.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.rbac.dto.response.UserDirectoryItemResponse;
import top.egon.mario.rbac.po.UserPo;
import top.egon.mario.rbac.po.enums.RbacStatus;
import top.egon.mario.rbac.repository.UserRepository;
import top.egon.mario.rbac.service.RbacException;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Exposes the minimum identity fields required by ordinary product modules.
 */
@Component
public class RbacUserDirectoryFacade {

    private static final int MAX_PAGE_SIZE = 20;

    private final UserRepository userRepository;

    public RbacUserDirectoryFacade(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<UserDirectoryItemResponse> search(String keyword, Long excludedUserId, int page, int size) {
        String normalizedKeyword = requireKeyword(keyword);
        if (excludedUserId == null) {
            throw new RbacException("RBAC_USER_DIRECTORY_EXCLUDED_USER_REQUIRED",
                    "current user id is required");
        }
        PageRequest pageable = PageRequest.of(Math.max(page, 0), normalizeSize(size),
                Sort.by(Sort.Order.asc("nickname").nullsLast(), Sort.Order.asc("id")));
        return userRepository.searchDirectory(normalizedKeyword, excludedUserId, RbacStatus.ENABLED, pageable)
                .map(this::toView);
    }

    @Transactional(readOnly = true)
    public Optional<UserDirectoryItemResponse> findEnabledById(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return userRepository.findByIdAndDeletedFalse(userId)
                .filter(this::available)
                .map(this::toView);
    }

    @Transactional(readOnly = true)
    public Optional<UserDirectoryItemResponse> findEnabledByAccountNo(String accountNo) {
        if (!StringUtils.hasText(accountNo)) {
            return Optional.empty();
        }
        return userRepository.findByAccountNoAndDeletedFalse(accountNo.trim())
                .filter(this::available)
                .map(this::toView);
    }

    @Transactional(readOnly = true)
    public Map<Long, UserDirectoryItemResponse> findEnabledByIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, UserDirectoryItemResponse> users = new LinkedHashMap<>();
        userRepository.findByIdInAndDeletedFalse(userIds).stream()
                .filter(this::available)
                .map(this::toView)
                .forEach(user -> users.put(user.userId(), user));
        return Map.copyOf(users);
    }

    private UserDirectoryItemResponse toView(UserPo user) {
        return new UserDirectoryItemResponse(
                user.getId(),
                user.getAccountNo(),
                StringUtils.hasText(user.getNickname()) ? user.getNickname().trim() : user.getAccountNo(),
                user.getAvatarUrl()
        );
    }

    private boolean available(UserPo user) {
        return RbacStatus.ENABLED.equals(user.getStatus()) && !user.isLocked() && !user.isDeleted();
    }

    private String requireKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            throw new RbacException("RBAC_USER_DIRECTORY_KEYWORD_REQUIRED", "user search keyword is required");
        }
        return keyword.trim();
    }

    private int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
