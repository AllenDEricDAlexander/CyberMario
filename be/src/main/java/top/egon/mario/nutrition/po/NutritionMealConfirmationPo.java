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
import top.egon.mario.nutrition.po.enums.NutritionConfirmationStatus;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "nutrition_meal_confirmation", uniqueConstraints = {
        @UniqueConstraint(name = "uk_nutrition_meal_confirmation_member",
                columnNames = {"meal_plan_id", "member_profile_id"})
})
public class NutritionMealConfirmationPo extends BaseAuditablePo {

    @Column(name = "family_id", nullable = false)
    private Long familyId;

    @Column(name = "meal_plan_id", nullable = false)
    private Long mealPlanId;

    @Column(name = "member_profile_id", nullable = false)
    private Long memberProfileId;

    @Column(name = "confirmed_by_user_id")
    private Long confirmedByUserId;

    @Column(name = "proxy_by_user_id")
    private Long proxyByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "confirmation_status", nullable = false, length = 32)
    private NutritionConfirmationStatus confirmationStatus = NutritionConfirmationStatus.PENDING;

    @Column(name = "eat_at_home", nullable = false)
    private boolean eatAtHome = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "selected_meal_types", nullable = false, columnDefinition = "jsonb")
    private String selectedMealTypes = "[]";

    @Column(name = "risk_confirmed", nullable = false)
    private boolean riskConfirmed;

    @Column(name = "risk_confirmation_note", length = 512)
    private String riskConfirmationNote;

    @Column(name = "remark", length = 512)
    private String remark;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
