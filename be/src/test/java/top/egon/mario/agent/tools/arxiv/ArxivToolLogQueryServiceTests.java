package top.egon.mario.agent.tools.arxiv;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import top.egon.mario.agent.tools.arxiv.repository.ArxivToolLogRepository;
import top.egon.mario.rag.service.RagException;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Verifies arXiv tool logs are restricted to super administrators.
 */
class ArxivToolLogQueryServiceTests {

    @Test
    void pageRejectsNonSuperAdminPrincipal() {
        ArxivToolLogQueryService service = new ArxivToolLogQueryService(mock(ArxivToolLogRepository.class));

        assertThatThrownBy(() -> service.page(PageRequest.of(0, 20),
                new RbacPrincipal(8L, "luigi", Set.of("CHAT_BASIC"), Set.of(), "v1")))
                .isInstanceOf(RagException.class)
                .extracting("code")
                .isEqualTo("ARXIV_LOG_FORBIDDEN");
    }

}
