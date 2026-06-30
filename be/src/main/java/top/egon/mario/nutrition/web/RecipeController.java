package top.egon.mario.nutrition.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.nutrition.dto.request.CreateRecipeRequest;
import top.egon.mario.nutrition.dto.request.CreateStandardFoodRequest;
import top.egon.mario.nutrition.dto.response.RecipeResponse;
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

    @GetMapping("/families/{familyId}/recipes")
    public Mono<ApiResponse<List<RecipeResponse>>> familyRecipes(@PathVariable @Min(1) Long familyId,
                                                                 @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> recipeService.listFamilyRecipes(familyId, actorId(principal)));
    }

    @PostMapping("/families/{familyId}/recipes")
    public Mono<ApiResponse<RecipeResponse>> createFamilyRecipe(@PathVariable @Min(1) Long familyId,
                                                                @Valid @RequestBody CreateRecipeRequest request,
                                                                @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> recipeService.createFamilyRecipe(familyId, request, actorId(principal)));
    }
}
