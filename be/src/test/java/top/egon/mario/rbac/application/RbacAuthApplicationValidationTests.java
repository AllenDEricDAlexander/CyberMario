package top.egon.mario.rbac.application;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import top.egon.mario.rbac.dto.request.LoginRequest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies application services enforce Bean Validation when called directly.
 */
@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class RbacAuthApplicationValidationTests {

    @Autowired
    private RbacAuthApplication authApplication;

    @MockitoBean
    private ChatModel chatModel;

    @Test
    void loginRejectsInvalidRequestBeforeBusinessLogic() {
        assertThatThrownBy(() -> authApplication.login(new LoginRequest("", "", ""), "127.0.0.1", "test"))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("account")
                .hasMessageContaining("encryptedPassword")
                .hasMessageContaining("passwordKeyId");
    }

}
