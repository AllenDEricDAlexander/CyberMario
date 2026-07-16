package top.egon.mario.nutrition.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Physically removes a family aggregate in dependency order.
 */
@Repository
@RequiredArgsConstructor
public class NutritionFamilyCascadeRepository {

    private static final List<String> FAMILY_SCOPED_TABLES = List.of(
            "nutrition_record_adjustment",
            "nutrition_record",
            "nutrition_extra_food_record",
            "nutrition_report_snapshot",
            "nutrition_food_price_record",
            "nutrition_shopping_list_item",
            "nutrition_shopping_list",
            "nutrition_meal_confirmation_item",
            "nutrition_meal_confirmation",
            "nutrition_meal_operation_log",
            "nutrition_meal_plan_item",
            "nutrition_meal_plan",
            "nutrition_risk_check_result",
            "nutrition_budget_snapshot",
            "nutrition_budget_rule",
            "nutrition_ai_recommendation",
            "nutrition_ai_recommendation_job",
            "nutrition_recipe_ingredient",
            "nutrition_recipe_step",
            "nutrition_recipe",
            "nutrition_import_job",
            "nutrition_health_profile",
            "nutrition_data_grant",
            "nutrition_clan_family",
            "nutrition_member_profile"
    );

    private final EntityManager entityManager;

    public void deleteFamilyAggregate(Long familyId) {
        deleteMemberProfileAndFamilyRoleBindings(familyId);
        deleteImportErrors(familyId);
        FAMILY_SCOPED_TABLES.forEach(table -> deleteFamilyRows(table, familyId));
        entityManager.createNativeQuery("DELETE FROM nutrition_family WHERE id = :familyId")
                .setParameter("familyId", familyId)
                .executeUpdate();
        entityManager.clear();
    }

    List<String> familyScopedTables() {
        return FAMILY_SCOPED_TABLES;
    }

    private void deleteMemberProfileAndFamilyRoleBindings(Long familyId) {
        entityManager.createNativeQuery("""
                        DELETE FROM nutrition_scoped_role_binding
                        WHERE (scope_type = 'FAMILY' AND scope_id = :familyId)
                           OR (scope_type = 'MEMBER_PROFILE' AND scope_id IN (
                               SELECT id
                               FROM nutrition_member_profile
                               WHERE family_id = :familyId
                           ))
                           OR (subject_type = 'MEMBER_PROFILE' AND subject_id IN (
                               SELECT id
                               FROM nutrition_member_profile
                               WHERE family_id = :familyId
                           ))
                        """)
                .setParameter("familyId", familyId)
                .executeUpdate();
    }

    private void deleteImportErrors(Long familyId) {
        entityManager.createNativeQuery("""
                        DELETE FROM nutrition_import_error
                        WHERE import_job_id IN (
                            SELECT id
                            FROM nutrition_import_job
                            WHERE family_id = :familyId
                        )
                        """)
                .setParameter("familyId", familyId)
                .executeUpdate();
    }

    private void deleteFamilyRows(String table, Long familyId) {
        entityManager.createNativeQuery("DELETE FROM " + table + " WHERE family_id = :familyId")
                .setParameter("familyId", familyId)
                .executeUpdate();
    }
}
