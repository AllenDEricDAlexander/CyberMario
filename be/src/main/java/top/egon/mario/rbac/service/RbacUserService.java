package top.egon.mario.rbac.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import top.egon.mario.rbac.dto.request.CreateUserRequest;
import top.egon.mario.rbac.dto.request.UpdateUserRequest;
import top.egon.mario.rbac.dto.response.UserResponse;
import top.egon.mario.rbac.po.UserPo;

import java.util.Collection;
import java.util.Set;

/**
 * User management service for account CRUD and direct role assignment.
 */
public interface RbacUserService {

    UserResponse createUser(@Valid @NotNull CreateUserRequest request, Long actorUserId);

    UserPo getUserPo(@NotNull Long userId);

    UserResponse getUser(@NotNull Long userId);

    Page<UserResponse> getUserPage(@NotNull Pageable pageable);

    UserResponse updateUser(@NotNull Long userId, @Valid @NotNull UpdateUserRequest request);

    void resetPassword(@NotNull Long userId, @NotBlank String newPassword);

    void updateStatus(@NotNull Long userId, @NotNull top.egon.mario.rbac.dto.enums.RbacStatus status);

    void deleteUser(@NotNull Long userId, Long actorUserId);

    void markLoginSuccess(@NotNull Long userId);

    void replaceUserRoles(@NotNull Long userId, Collection<Long> roleIds, Long actorUserId);

    Set<Long> getDirectRoleIds(@NotNull Long userId);

}
