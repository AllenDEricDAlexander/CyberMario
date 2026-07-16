package top.egon.mario.investment.portfolio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.access.InvestmentAccessService;
import top.egon.mario.investment.portfolio.query.InvestmentPortfolioQueryService;
import top.egon.mario.investment.portfolio.web.dto.InvestmentFillMarkerResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvestmentPortfolioMarkerContractTests {

    private static final Instant FROM = Instant.parse("2026-07-16T00:00:00Z");

    @Mock private InvestmentAccessService accessService;
    @Mock private NamedParameterJdbcTemplate jdbcTemplate;

    private InvestmentPortfolioQueryService service;

    @BeforeEach
    void setUp() {
        service = new InvestmentPortfolioQueryService(accessService, jdbcTemplate);
    }

    @Test
    void markerRequestRequiresInstrumentWindowAndEnforcesWindowAndPageBounds() {
        assertThatThrownBy(() -> service.fills(101L, 21L, null, FROM, FROM.plusSeconds(60), 0, 100))
                .isInstanceOf(InvestmentException.class);
        assertThatThrownBy(() -> service.fills(
                101L, 21L, 501L, FROM, FROM.plus(Duration.ofDays(32)), 0, 100))
                .isInstanceOf(InvestmentException.class);
        assertThatThrownBy(() -> service.fills(
                101L, 21L, 501L, FROM, FROM.plusSeconds(60), 0, 501))
                .isInstanceOf(InvestmentException.class);
        verify(jdbcTemplate, never()).query(anyString(), any(MapSqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<InvestmentFillMarkerResponse>>any());
    }

    @Test
    void markerQueryIsOwnerFirstAndUsesStableEventTimeIdOrdering() {
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<InvestmentFillMarkerResponse>>any()))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);

        service.fills(101L, 21L, 501L, FROM, FROM.plusSeconds(60), 0, 100);

        verify(accessService).requireAccountOwner(21L, 101L);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(MapSqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<InvestmentFillMarkerResponse>>any());
        assertThat(sql.getValue()).contains("order by fill.filled_at asc, fill.id asc");
    }

    @Test
    void crossOwnerFailureRunsBeforeAnyPortfolioSqlAndMarkerShapeClassifiesLiquidation() {
        InvestmentException forbidden = new InvestmentException(
                InvestmentErrorCode.FORBIDDEN, "denied");
        org.mockito.Mockito.doThrow(forbidden).when(accessService).requireAccountOwner(21L, 999L);

        assertThatThrownBy(() -> service.fills(
                999L, 21L, 501L, FROM, FROM.plusSeconds(60), 0, 100))
                .isSameAs(forbidden);
        verify(jdbcTemplate, never()).query(anyString(), any(MapSqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<InvestmentFillMarkerResponse>>any());

        InvestmentFillMarkerResponse marker = new InvestmentFillMarkerResponse(
                61L, 501L, FROM, FROM, "SELL", "CLOSE", "LIQUIDATION",
                "LIQUIDATION_FILL", "99.9", "1", true);
        assertThat(marker).extracting(
                InvestmentFillMarkerResponse::marketBarOpenTime,
                InvestmentFillMarkerResponse::side,
                InvestmentFillMarkerResponse::actionType,
                InvestmentFillMarkerResponse::orderOrigin,
                InvestmentFillMarkerResponse::eventType,
                InvestmentFillMarkerResponse::price,
                InvestmentFillMarkerResponse::quantity,
                InvestmentFillMarkerResponse::liquidation)
                .containsExactly(FROM, "SELL", "CLOSE", "LIQUIDATION",
                        "LIQUIDATION_FILL", "99.9", "1", true);
    }
}
