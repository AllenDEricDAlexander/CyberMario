package top.egon.mario.nutrition.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.nutrition.dto.request.CreateFoodPriceRecordRequest;
import top.egon.mario.nutrition.dto.request.UpdateShoppingListItemRequest;
import top.egon.mario.nutrition.dto.response.FoodPriceRecordResponse;
import top.egon.mario.nutrition.dto.response.ShoppingListItemResponse;
import top.egon.mario.nutrition.dto.response.ShoppingListResponse;
import top.egon.mario.nutrition.service.ShoppingListService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

/**
 * Shopping list and food price endpoints for the nutrition MVP.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/nutrition/families/{familyId}")
@Validated
public class ShoppingListController extends ReactiveNutritionSupport {

    private final ShoppingListService shoppingListService;

    @PostMapping("/meal-plans/{mealPlanId}/shopping-list/generate")
    public Mono<ApiResponse<ShoppingListResponse>> generateShoppingList(
            @PathVariable @Min(1) Long familyId,
            @PathVariable @Min(1) Long mealPlanId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> shoppingListService.generateShoppingList(
                familyId, mealPlanId, actorId(principal)));
    }

    @GetMapping("/shopping-lists/{shoppingListId}")
    public Mono<ApiResponse<ShoppingListResponse>> shoppingList(
            @PathVariable @Min(1) Long familyId,
            @PathVariable @Min(1) Long shoppingListId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> shoppingListService.getShoppingList(
                familyId, shoppingListId, actorId(principal)));
    }

    @PutMapping("/shopping-lists/{shoppingListId}/items/{itemId}")
    public Mono<ApiResponse<ShoppingListItemResponse>> updateShoppingListItem(
            @PathVariable @Min(1) Long familyId,
            @PathVariable @Min(1) Long shoppingListId,
            @PathVariable @Min(1) Long itemId,
            @Valid @RequestBody UpdateShoppingListItemRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> shoppingListService.updateShoppingListItem(
                familyId, shoppingListId, itemId, request, actorId(principal)));
    }

    @PostMapping("/price-records")
    public Mono<ApiResponse<FoodPriceRecordResponse>> createPriceRecord(
            @PathVariable @Min(1) Long familyId,
            @Valid @RequestBody CreateFoodPriceRecordRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> shoppingListService.createPriceRecord(
                familyId, request, actorId(principal)));
    }
}
