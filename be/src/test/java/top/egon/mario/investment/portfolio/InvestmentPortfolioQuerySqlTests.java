package top.egon.mario.investment.portfolio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import top.egon.mario.investment.common.access.InvestmentAccessService;
import top.egon.mario.investment.portfolio.query.InvestmentPortfolioQueryService;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Executes every portfolio projection against the migrated PostgreSQL-compatible schema. */
@SpringBootTest
class InvestmentPortfolioQuerySqlTests {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    private InvestmentPortfolioQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new InvestmentPortfolioQueryService(mock(InvestmentAccessService.class), jdbcTemplate);
    }

    @Test
    void allPortfolioQueriesExecuteAgainstTheMigratedSchema() {
        Instant from = Instant.parse("2026-07-01T00:00:00Z");
        Instant to = from.plusSeconds(86_400);

        assertThat(queryService.fills(101L, 31L, 501L, from, to, 0, 20)).isEmpty();
        assertThat(queryService.positions(101L, 31L)).isEmpty();
        assertThat(queryService.ledger(101L, 31L, 0, 20)).isEmpty();
        assertThat(queryService.equity(101L, 31L, 0, 20)).isEmpty();
        assertThat(queryService.workspacePositions(11L, to)).isEmpty();
        assertThat(queryService.workspaceSummary(11L, to)).satisfies(summary -> {
            assertThat(summary.accountCount()).isZero();
            assertThat(summary.positionCount()).isZero();
            assertThat(summary.equity()).isEqualByComparingTo("0");
        });
    }
}
