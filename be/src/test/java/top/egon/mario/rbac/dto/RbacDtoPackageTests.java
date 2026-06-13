package top.egon.mario.rbac.dto;

import org.junit.jupiter.api.Test;
import top.egon.mario.rbac.dto.request.CreateUserRequest;
import top.egon.mario.rbac.dto.request.PermissionRequest;
import top.egon.mario.rbac.dto.response.PermissionResponse;
import top.egon.mario.rbac.dto.response.UserResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies RBAC API DTOs keep request and response packages separate.
 */
class RbacDtoPackageTests {

    @Test
    void requestDtosLiveInRequestPackage() {
        assertThat(CreateUserRequest.class.getPackageName()).isEqualTo("top.egon.mario.rbac.dto.request");
        assertThat(PermissionRequest.class.getPackageName()).isEqualTo("top.egon.mario.rbac.dto.request");
    }

    @Test
    void responseDtosLiveInResponsePackage() {
        assertThat(UserResponse.class.getPackageName()).isEqualTo("top.egon.mario.rbac.dto.response");
        assertThat(PermissionResponse.class.getPackageName()).isEqualTo("top.egon.mario.rbac.dto.response");
    }

}
