package top.egon.mario.rbac.service;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import top.egon.mario.rbac.dto.request.CreateUserRequest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies RBAC service proxies enforce Bean Validation before business logic.
 */
@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class RbacUserServiceValidationTests {

    @Autowired
    private RbacUserService userService;

    @MockitoBean
    private ChatModel chatModel;

    @Test
    void createUserRejectsInvalidRequestBeforeBusinessLogic() {
        CreateUserRequest request = new CreateUserRequest();

        assertThatThrownBy(() -> userService.createUser(request, 1L))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("accountNo")
                .hasMessageContaining("username")
                .hasMessageContaining("initialPassword");
    }

    @Test
    void getUserRejectsMissingIdBeforeBusinessLogic() {
        assertThatThrownBy(() -> userService.getUser(null))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("userId");
    }

}
