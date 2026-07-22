package top.egon.mario.rbac.po;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import top.egon.mario.rag.repository.RagKnowledgeBaseUserRepository;
import top.egon.mario.rbac.dto.enums.RbacStatus;
import top.egon.mario.rbac.dto.response.UserResponse;
import top.egon.mario.rbac.repository.OneTimeTokenRepository;
import top.egon.mario.rbac.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class RbacEnumMappingTests {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OneTimeTokenRepository oneTimeTokenRepository;
    @Autowired
    private RagKnowledgeBaseUserRepository knowledgeBaseUserRepository;

    @BeforeEach
    void setUp() {
        knowledgeBaseUserRepository.deleteAll();
        oneTimeTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void enumResponseContainsCodeAndDescription() throws Exception {
        UserResponse response = new UserResponse();
        response.setStatus(RbacStatus.DISABLED);

        JsonNode statusNode = objectMapper.readTree(objectMapper.writeValueAsString(response)).get("status");

        assertThat(statusNode.get("code").asInt()).isEqualTo(0);
        assertThat(statusNode.get("desc").asText()).isEqualTo("禁用");
    }

    @Test
    void jpaStoresEnumCodeAsInteger() {
        UserPo user = new UserPo();
        user.setUsername("enum-test");
        user.setPasswordHash("hash");
        user.setStatus(top.egon.mario.rbac.po.enums.RbacStatus.DISABLED);

        UserPo savedUser = userRepository.save(user);

        Integer storedStatus = jdbcTemplate.queryForObject(
                "select status from sys_user where id = ?",
                Integer.class,
                savedUser.getId()
        );
        assertThat(storedStatus).isEqualTo(0);
    }

}
