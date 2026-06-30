package top.egon.mario.nutrition.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import top.egon.mario.common.entity.BaseAuditablePo;

@Getter
@Setter
@Entity
@Table(name = "nutrition_recipe_step")
public class NutritionRecipeStepPo extends BaseAuditablePo {

    @Column(name = "family_id")
    private Long familyId;

    @Column(name = "recipe_id", nullable = false)
    private Long recipeId;

    @Column(name = "step_no", nullable = false)
    private int stepNo;

    @Column(name = "title", length = 128)
    private String title;

    @Column(name = "instruction", nullable = false, columnDefinition = "text")
    private String instruction = "";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "media_metadata", nullable = false, columnDefinition = "jsonb")
    private String mediaMetadata = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
