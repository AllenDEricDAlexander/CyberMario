package top.egon.mario.rbac.service;

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

    UserResponse createUser(CreateUserRequest request, Long actorUserId);

    UserPo getUserPo(Long userId);

    UserResponse getUser(Long userId);

    Page<UserResponse> getUserPage(Pageable pageable);

    UserResponse updateUser(Long userId, UpdateUserRequest request);

    void resetPassword(Long userId, String newPassword);

    void updateStatus(Long userId, top.egon.mario.rbac.dto.enums.RbacStatus status);

    void deleteUser(Long userId, Long actorUserId);

    void markLoginSuccess(Long userId);

    void replaceUserRoles(Long userId, Collection<Long> roleIds, Long actorUserId);

    Set<Long> getDirectRoleIds(Long userId);

}
