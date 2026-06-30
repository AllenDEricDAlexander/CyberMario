package top.egon.mario.nutrition.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import top.egon.mario.common.entity.BaseAuditablePo;
import top.egon.mario.nutrition.po.enums.NutritionStatus;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "nutrition_clan_family", uniqueConstraints = {
        @UniqueConstraint(name = "uk_nutrition_clan_family", columnNames = {"clan_id", "family_id"})
})
public class NutritionClanFamilyPo extends BaseAuditablePo {

    @Column(name = "clan_id", nullable = false)
    private Long clanId;

    @Column(name = "family_id", nullable = false)
    private Long familyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_status", nullable = false, length = 32)
    private NutritionStatus relationStatus = NutritionStatus.ACTIVE;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt = Instant.now();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
