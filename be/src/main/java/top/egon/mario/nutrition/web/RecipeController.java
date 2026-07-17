package top.egon.mario.nutrition.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.nutrition.dto.request.CreateRecipeRequest;
import top.egon.mario.nutrition.dto.request.CreateStandardFoodRequest;
import top.egon.mario.nutrition.dto.request.UpdateRecipeIngredientMappingRequest;
import top.egon.mario.nutrition.dto.response.RecipeIngredientResponse;
import top.egon.mario.nutrition.dto.response.RecipeResponse;
import top.egon.mario.nutrition.dto.response.RecipeValidationResponse;
import top.egon.mario.nutrition.dto.response.StandardFoodResponse;
import top.egon.mario.nutrition.service.RecipeService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

/**
 * Standard food and family recipe endpoints for the nutrition MVP.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/nutrition")
@Validated
public class RecipeController extends ReactiveNutritionSupport {

    private final RecipeService recipeService;

    @GetMapping("/platform/standard-foods")
    public Mono<ApiResponse<List<StandardFoodResponse>>> standardFoods(
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> recipeService.listStandardFoods(principal));
    }

    @PostMapping("/platform/standard-foods")
    public Mono<ApiResponse<StandardFoodResponse>> createStandardFood(
            @Valid @RequestBody CreateStandardFoodRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> recipeService.createStandardFood(request, principal));
    }

    @PutMapping("/platform/standard-foods/{foodId}")
    public Mono<ApiResponse<StandardFoodResponse>> updateStandardFood(
            @PathVariable @Min(1) Long foodId,
            @Valid @RequestBody CreateStandardFoodRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> recipeService.updateStandardFood(foodId, request, principal));
    }

    @DeleteMapping("/platform/standard-foods/{foodId}")
    public Mono<ApiResponse<StandardFoodResponse>> deactivateStandardFood(
            @PathVariable @Min(1) Long foodId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> recipeService.deactivateStandardFood(foodId, principal));
    }

    @GetMapping("/families/{familyId}/standard-foods")
    public Mono<ApiResponse<List<StandardFoodResponse>>> familyStandardFoods(
            @PathVariable @Min(1) Long familyId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> recipeService.listFamilyStandardFoods(familyId, actorId(principal)));
    }

    @GetMapping("/platform/recipes")
    public Mono<ApiResponse<List<RecipeResponse>>> platformRecipes(
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> recipeService.listPlatformRecipes(principal));
    }

    @PostMapping("/platform/recipes")
    public Mono<ApiResponse<RecipeResponse>> createPlatformRecipe(
            @Valid @RequestBody CreateRecipeRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> recipeService.createPlatformRecipe(request, principal));
    }

    @PutMapping("/platform/recipes/{recipeId}")
    public Mono<ApiResponse<RecipeResponse>> updatePlatformRecipe(
            @PathVariable @Min(1) Long recipeId,
            @Valid @RequestBody CreateRecipeRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> recipeService.updatePlatformRecipe(recipeId, request, principal));
    }

    @DeleteMapping("/platform/recipes/{recipeId}")
    public Mono<ApiResponse<RecipeResponse>> deactivatePlatformRecipe(
            @PathVariable @Min(1) Long recipeId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> recipeService.deactivateRecipe(recipeId, principal));
    }

    @GetMapping("/families/{familyId}/recipes")
    public Mono<ApiResponse<List<RecipeResponse>>> familyRecipes(@PathVariable @Min(1) Long familyId,
                                                                 @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> recipeService.listFamilyRecipes(familyId, actorId(principal)));
    }

    @GetMapping("/families/{familyId}/recipes/meal-plan-candidates")
    public Mono<ApiResponse<List<RecipeResponse>>> mealPlanCandidates(
            @PathVariable @Min(1) Long familyId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> recipeService.listMealPlanCandidates(familyId, actorId(principal)));
    }

    @PostMapping("/families/{familyId}/recipes")
    public Mono<ApiResponse<RecipeResponse>> createFamilyRecipe(@PathVariable @Min(1) Long familyId,
                                                                @Valid @RequestBody CreateRecipeRequest request,
                                                                @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> recipeService.createFamilyRecipe(familyId, request, actorId(principal)));
    }

    @GetMapping("/families/{familyId}/recipes/{recipeId}")
    public Mono<ApiResponse<RecipeResponse>> familyRecipe(
            @PathVariable @Min(1) Long familyId,
            @PathVariable @Min(1) Long recipeId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> recipeService.getRecipe(familyId, recipeId, actorId(principal)));
    }

    @PutMapping("/families/{familyId}/recipes/{recipeId}")
    public Mono<ApiResponse<RecipeResponse>> updateFamilyRecipe(
            @PathVariable @Min(1) Long familyId,
            @PathVariable @Min(1) Long recipeId,
            @Valid @RequestBody CreateRecipeRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> recipeService.updateFamilyRecipe(
                familyId, recipeId, request, actorId(principal)));
    }

    @DeleteMapping("/families/{familyId}/recipes/{recipeId}")
    public Mono<ApiResponse<RecipeResponse>> deactivateFamilyRecipe(
            @PathVariable @Min(1) Long familyId,
            @PathVariable @Min(1) Long recipeId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> recipeService.deactivateFamilyRecipe(
                familyId, recipeId, actorId(principal)));
    }

    @PutMapping("/families/{familyId}/recipes/{recipeId}/ingredients/{ingredientId}/mapping")
    public Mono<ApiResponse<RecipeIngredientResponse>> updateIngredientMapping(
            @PathVariable @Min(1) Long familyId,
            @PathVariable @Min(1) Long recipeId,
            @PathVariable @Min(1) Long ingredientId,
            @Valid @RequestBody UpdateRecipeIngredientMappingRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> recipeService.updateIngredientMapping(
                familyId, recipeId, ingredientId, request, actorId(principal)));
    }

    @GetMapping("/families/{familyId}/recipes/{recipeId}/validation")
    public Mono<ApiResponse<RecipeValidationResponse>> validateRecipe(
            @PathVariable @Min(1) Long familyId,
            @PathVariable @Min(1) Long recipeId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> recipeService.validateRecipe(familyId, recipeId, actorId(principal)));
    }
}
