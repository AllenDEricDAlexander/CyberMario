package top.egon.mario.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class RbacSecurityConfigPasswordEncoderTests {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ReactiveUserDetailsService reactiveUserDetailsService;

    @Test
    void encodesNewPasswordsWithArgon2idAndKeepsLegacyBcryptReadable() {
        String rawPassword = "CyberMario-Admin#2026";

        String encoded = passwordEncoder.encode(rawPassword);

        assertThat(encoded).startsWith("{argon2id}");
        assertThat(passwordEncoder.matches(rawPassword, encoded)).isTrue();

        String legacyBcrypt = new BCryptPasswordEncoder().encode(rawPassword);
        assertThat(passwordEncoder.matches(rawPassword, legacyBcrypt)).isTrue();
        assertThat(passwordEncoder.upgradeEncoding(legacyBcrypt)).isTrue();
    }

    @Test
    void disablesSpringBootGeneratedReactiveUser() {
        StepVerifier.create(reactiveUserDetailsService.findByUsername("user"))
                .verifyComplete();
    }

}
