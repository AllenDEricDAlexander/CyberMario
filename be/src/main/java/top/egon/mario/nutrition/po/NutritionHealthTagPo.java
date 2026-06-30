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

@Getter
@Setter
@Entity
@Table(name = "nutrition_health_tag", uniqueConstraints = {
        @UniqueConstraint(name = "uk_nutrition_health_tag_code", columnNames = {"tag_type", "tag_code"})
})
public class NutritionHealthTagPo extends BaseAuditablePo {

    @Column(name = "tag_type", nullable = false, length = 64)
    private String tagType;

    @Column(name = "tag_code", nullable = false, length = 128)
    private String tagCode;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private NutritionStatus status = NutritionStatus.ACTIVE;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
