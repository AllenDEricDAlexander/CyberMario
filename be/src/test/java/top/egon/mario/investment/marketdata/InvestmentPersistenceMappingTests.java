package top.egon.mario.investment.marketdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import top.egon.mario.common.entity.BaseAuditablePo;
import top.egon.mario.investment.marketdata.po.InvestmentContractSpecPo;
import top.egon.mario.investment.marketdata.po.InvestmentDataQualityIssuePo;
import top.egon.mario.investment.marketdata.po.InvestmentDataSourcePo;
import top.egon.mario.investment.marketdata.po.InvestmentIngestCursorPo;
import top.egon.mario.investment.marketdata.po.InvestmentInstrumentPo;
import top.egon.mario.investment.marketdata.po.InvestmentInstrumentSourcePo;
import top.egon.mario.investment.marketdata.po.InvestmentPositionTierPo;
import top.egon.mario.investment.marketdata.po.InvestmentVenuePo;
import top.egon.mario.investment.marketdata.repository.jdbc.model.ContractQuoteRow;
import top.egon.mario.investment.marketdata.repository.jdbc.model.FundingRateRow;
import top.egon.mario.investment.marketdata.repository.jdbc.model.MarketBarDailyRow;
import top.egon.mario.investment.marketdata.repository.jdbc.model.MarketBarIntradayRow;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InvestmentPersistenceMappingTests {

    @Test
    void ordinaryCatalogAndQualityMappingsUseJpaWithStandardAuditFields() throws NoSuchFieldException {
        assertThat(InvestmentVenuePo.class.getSuperclass()).isEqualTo(BaseAuditablePo.class);
        assertThat(InvestmentDataSourcePo.class.getSuperclass()).isEqualTo(BaseAuditablePo.class);
        assertThat(InvestmentInstrumentPo.class.getSuperclass()).isEqualTo(BaseAuditablePo.class);
        assertThat(InvestmentInstrumentSourcePo.class.getSuperclass()).isEqualTo(BaseAuditablePo.class);
        assertThat(InvestmentDataQualityIssuePo.class.getSuperclass()).isEqualTo(BaseAuditablePo.class);

        for (String auditField : List.of("id", "createdAt", "updatedAt", "createdBy", "updatedBy",
                "version", "deleted")) {
            assertThat(BaseAuditablePo.class.getDeclaredField(auditField)).as(auditField).isNotNull();
        }
        assertColumn(InvestmentVenuePo.class, "metadataJson", "metadata_json", false);
        assertColumn(InvestmentInstrumentPo.class, "deliveryTime", "delivery_time", true);
        assertColumn(InvestmentDataQualityIssuePo.class, "detailsJson", "details_json", false);
    }

    @Test
    void normalizedSpecTierAndCursorAreJpaMappedWithoutInventedAuditColumns() {
        for (Class<?> type : List.of(InvestmentContractSpecPo.class, InvestmentPositionTierPo.class,
                InvestmentIngestCursorPo.class)) {
            assertThat(type).hasAnnotation(Entity.class);
            assertThat(type.getSuperclass()).isEqualTo(Object.class);
        }
    }

    @Test
    void highVolumeFactsAreJdbcRecordsAndNeverCompositeIdJpaEntities() {
        for (Class<?> type : List.of(MarketBarDailyRow.class, MarketBarIntradayRow.class,
                FundingRateRow.class, ContractQuoteRow.class)) {
            assertThat(type).isRecord();
            assertThat(type.getAnnotation(Entity.class)).isNull();
        }
    }

    @Test
    void jdbcInstantParametersUseUtcOffsetDateTimeForPostgresCompatibility() throws ReflectiveOperationException {
        Class<?> support = Class.forName(
                "top.egon.mario.investment.marketdata.repository.jdbc.JdbcMarketDataSupport");
        Method scalar = support.getDeclaredMethod("instantParameter", Instant.class);
        Method collection = support.getDeclaredMethod("instantParameters", Collection.class);
        scalar.setAccessible(true);
        collection.setAccessible(true);
        Instant first = Instant.parse("2030-01-01T00:00:00Z");
        Instant second = first.plusSeconds(60);

        assertThat(scalar.invoke(null, first))
                .isInstanceOf(OffsetDateTime.class)
                .isEqualTo(first.atOffset(ZoneOffset.UTC));
        assertThat(scalar.invoke(null, new Object[]{null})).isNull();
        assertThat(collection.invoke(null, List.of(first, second)))
                .isEqualTo(List.of(first.atOffset(ZoneOffset.UTC), second.atOffset(ZoneOffset.UTC)));
    }

    private void assertColumn(Class<?> owner, String fieldName, String columnName, boolean nullable)
            throws NoSuchFieldException {
        Field field = owner.getDeclaredField(fieldName);
        Column column = field.getAnnotation(Column.class);
        assertThat(column).isNotNull();
        assertThat(column.name()).isEqualTo(columnName);
        assertThat(column.nullable()).isEqualTo(nullable);
    }
}
