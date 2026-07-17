package top.egon.mario.nutrition.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import top.egon.mario.nutrition.dto.request.CreateFoodPriceRecordRequest;
import top.egon.mario.nutrition.dto.request.TransitionShoppingListRequest;
import top.egon.mario.nutrition.dto.request.UpdateShoppingListItemRequest;
import top.egon.mario.nutrition.dto.response.FoodPriceRecordResponse;
import top.egon.mario.nutrition.dto.response.ShoppingListItemResponse;
import top.egon.mario.nutrition.dto.response.ShoppingListResponse;
import top.egon.mario.nutrition.po.NutritionFoodPriceRecordPo;
import top.egon.mario.nutrition.po.NutritionMealConfirmationItemPo;
import top.egon.mario.nutrition.po.NutritionMealConfirmationPo;
import top.egon.mario.nutrition.po.NutritionMealPlanItemPo;
import top.egon.mario.nutrition.po.NutritionMealPlanPo;
import top.egon.mario.nutrition.po.NutritionRecipeIngredientPo;
import top.egon.mario.nutrition.po.NutritionRecipePo;
import top.egon.mario.nutrition.po.NutritionShoppingListItemPo;
import top.egon.mario.nutrition.po.NutritionShoppingListPo;
import top.egon.mario.nutrition.po.NutritionStandardFoodPo;
import top.egon.mario.nutrition.po.enums.NutritionConfirmationStatus;
import top.egon.mario.nutrition.po.enums.NutritionMealPlanStatus;
import top.egon.mario.nutrition.po.enums.NutritionRecipeSourceType;
import top.egon.mario.nutrition.po.enums.NutritionShoppingListStatus;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.repository.NutritionFoodPriceRecordRepository;
import top.egon.mario.nutrition.repository.NutritionMealConfirmationItemRepository;
import top.egon.mario.nutrition.repository.NutritionMealConfirmationRepository;
import top.egon.mario.nutrition.repository.NutritionMealPlanItemRepository;
import top.egon.mario.nutrition.repository.NutritionMealPlanRepository;
import top.egon.mario.nutrition.repository.NutritionRecipeIngredientRepository;
import top.egon.mario.nutrition.repository.NutritionRecipeRepository;
import top.egon.mario.nutrition.repository.NutritionShoppingListItemRepository;
import top.egon.mario.nutrition.repository.NutritionShoppingListRepository;
import top.egon.mario.nutrition.repository.NutritionStandardFoodRepository;
import top.egon.mario.nutrition.service.access.NutritionAccessService;
import top.egon.mario.nutrition.service.calculation.NutritionCalculationService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Application service for shopping list generation and food price records.
 */
@Service
@RequiredArgsConstructor
@Validated
public class ShoppingListService {

    private static final String ITEM_STATUS_PLANNED = "PLANNED";
    private static final String ITEM_STATUS_CHECKED = "CHECKED";
    private static final String ITEM_STATUS_PURCHASED = "PURCHASED";
    private static final String SOURCE_TYPE_SHOPPING_LIST = "SHOPPING_LIST";
    private static final Set<NutritionMealPlanStatus> PREVIEWABLE_MEAL_PLAN_STATUSES = EnumSet.of(
            NutritionMealPlanStatus.PUBLISHED,
            NutritionMealPlanStatus.CONFIRMING);

    private final NutritionShoppingListRepository shoppingListRepository;
    private final NutritionShoppingListItemRepository shoppingListItemRepository;
    private final NutritionFoodPriceRecordRepository priceRecordRepository;
    private final NutritionMealPlanRepository mealPlanRepository;
    private final NutritionMealPlanItemRepository mealPlanItemRepository;
    private final NutritionMealConfirmationRepository confirmationRepository;
    private final NutritionMealConfirmationItemRepository confirmationItemRepository;
    private final NutritionRecipeRepository recipeRepository;
    private final NutritionRecipeIngredientRepository recipeIngredientRepository;
    private final NutritionStandardFoodRepository standardFoodRepository;
    private final NutritionAccessService accessService;
    private final NutritionCalculationService calculationService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public ShoppingListResponse previewShoppingList(@NotNull Long familyId, @NotNull Long mealPlanId,
                                                    Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireReadFamily(userId, familyId);
        NutritionMealPlanPo mealPlan = mealPlanRepository.findByIdAndFamilyIdAndDeletedFalse(mealPlanId, familyId)
                .orElseThrow(() -> new NutritionException(
                        "NUTRITION_MEAL_PLAN_NOT_FOUND", "nutrition meal plan not found"));
        if (!PREVIEWABLE_MEAL_PLAN_STATUSES.contains(mealPlan.getStatus())) {
            throw invalidMealPlanStatus();
        }
        ShoppingDraft draft = calculateDraft(familyId, mealPlan);
        NutritionShoppingListPo preview = new NutritionShoppingListPo();
        preview.setFamilyId(familyId);
        preview.setMealPlanId(mealPlan.getId());
        preview.setListDate(mealPlan.getPlanDate());
        preview.setStatus(NutritionShoppingListStatus.DRAFT);
        preview.setTitle(mealPlan.getTitle() + " shopping list preview");
        preview.setGeneratedSnapshot(draft.generatedSnapshot());
        List<NutritionShoppingListItemPo> items = draft.accumulators().stream()
                .sorted(Comparator.comparing(IngredientAccumulator::rawFoodName))
                .map(accumulator -> createShoppingListItem(familyId, null, accumulator))
                .toList();
        preview.setEstimatedTotalPrice(estimatedTotal(items));
        return toResponse(preview, items);
    }

    @Transactional
    public ShoppingListResponse generateFinalShoppingList(@NotNull Long familyId, @NotNull Long mealPlanId,
                                                          Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireCookFamily(userId, familyId);
        NutritionMealPlanPo mealPlan = getLockedMealPlan(familyId, mealPlanId);
        if (mealPlan.getStatus() != NutritionMealPlanStatus.CONFIRM_CLOSED) {
            throw invalidMealPlanStatus();
        }
        ShoppingDraft draft = calculateDraft(familyId, mealPlan);
        List<NutritionShoppingListPo> existingLists = shoppingListRepository
                .findLockedByFamilyIdAndMealPlanIdAndDeletedFalseOrderByIdAsc(familyId, mealPlan.getId());
        Optional<NutritionShoppingListPo> matchingList = existingLists.stream()
                .filter(list -> list.getStatus() != NutritionShoppingListStatus.CANCELLED)
                .filter(list -> Objects.equals(list.getGeneratedSnapshot(), draft.generatedSnapshot()))
                .findFirst();
        if (matchingList.isPresent()) {
            NutritionShoppingListPo existingList = matchingList.orElseThrow();
            if (existingList.getStatus() == NutritionShoppingListStatus.DRAFT) {
                existingList.setStatus(NutritionShoppingListStatus.ACTIVE);
                shoppingListRepository.saveAndFlush(existingList);
            }
            return toResponse(existingList, shoppingListItemRepository
                    .findByShoppingListIdAndDeletedFalseOrderByIdAsc(existingList.getId()));
        }
        if (existingLists.stream().anyMatch(this::shoppingStarted)) {
            throw new NutritionException(
                    "NUTRITION_CONFIRMED_MENU_SHOPPING_STARTED",
                    "confirmed nutrition menu cannot change after shopping starts");
        }
        List<NutritionShoppingListPo> replacedLists = existingLists.stream()
                .filter(list -> list.getStatus() == NutritionShoppingListStatus.DRAFT
                        || list.getStatus() == NutritionShoppingListStatus.ACTIVE)
                .peek(list -> list.setStatus(NutritionShoppingListStatus.CANCELLED))
                .toList();
        if (!replacedLists.isEmpty()) {
            shoppingListRepository.saveAllAndFlush(replacedLists);
        }

        NutritionShoppingListPo shoppingList = new NutritionShoppingListPo();
        shoppingList.setFamilyId(familyId);
        shoppingList.setMealPlanId(mealPlan.getId());
        shoppingList.setListDate(mealPlan.getPlanDate());
        shoppingList.setStatus(NutritionShoppingListStatus.ACTIVE);
        shoppingList.setTitle(mealPlan.getTitle() + " shopping list");
        shoppingList.setGeneratedSnapshot(draft.generatedSnapshot());
        NutritionShoppingListPo savedList = shoppingListRepository.saveAndFlush(shoppingList);
        Long shoppingListId = savedList.getId();
        List<NutritionShoppingListItemPo> savedItems = draft.accumulators().stream()
                .sorted(Comparator.comparing(IngredientAccumulator::rawFoodName))
                .map(accumulator -> createShoppingListItem(familyId, shoppingListId, accumulator))
                .map(shoppingListItemRepository::save)
                .toList();
        savedList.setEstimatedTotalPrice(estimatedTotal(savedItems));
        shoppingListRepository.saveAndFlush(savedList);
        return toResponse(savedList, savedItems);
    }

    /**
     * Compatibility entry point retained for existing clients.
     */
    @Transactional
    public ShoppingListResponse generateShoppingList(@NotNull Long familyId, @NotNull Long mealPlanId,
                                                     Long actorId) {
        return generateFinalShoppingList(familyId, mealPlanId, actorId);
    }

    private ShoppingDraft calculateDraft(Long familyId, NutritionMealPlanPo mealPlan) {
        List<NutritionMealPlanItemPo> mealItems = mealPlanItemRepository
                .findByMealPlanIdAndStatusAndDeletedFalseOrderBySortOrderAscIdAsc(
                        mealPlan.getId(), NutritionStatus.ACTIVE);
        List<Long> recipeIds = mealItems.stream()
                .map(NutritionMealPlanItemPo::getRecipeId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, NutritionRecipePo> recipesById = recipeIds.isEmpty() ? Map.of() : recipeRepository
                .findByIdInAndStatusAndDeletedFalse(recipeIds, NutritionStatus.ACTIVE)
                .stream()
                .collect(Collectors.toMap(NutritionRecipePo::getId, Function.identity()));
        validateRecipesVisible(familyId, recipeIds, recipesById);
        Map<Long, List<NutritionRecipeIngredientPo>> ingredientsByRecipeId = recipeIds.isEmpty() ? Map.of()
                : recipeIngredientRepository.findByRecipeIdInAndDeletedFalseOrderByIdAsc(recipeIds)
                .stream()
                .collect(Collectors.groupingBy(NutritionRecipeIngredientPo::getRecipeId));
        Map<Long, NutritionStandardFoodPo> foodsById = standardFoods(ingredientsByRecipeId.values().stream()
                .flatMap(Collection::stream)
                .map(NutritionRecipeIngredientPo::getStandardFoodId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        List<NutritionMealConfirmationPo> confirmations = confirmationRepository
                .findByMealPlanIdAndConfirmationStatusAndDeletedFalse(
                        mealPlan.getId(), NutritionConfirmationStatus.CONFIRMED)
                .stream()
                .sorted(Comparator.comparing(NutritionMealConfirmationPo::getId))
                .toList();
        List<NutritionMealConfirmationItemPo> confirmationItems = confirmations.isEmpty() ? List.of()
                : confirmationItemRepository.findByConfirmationIdInAndDeletedFalseOrderByIdAsc(
                        confirmations.stream().map(NutritionMealConfirmationPo::getId).toList());
        Map<Long, NutritionMealPlanItemPo> mealItemsById = mealItems.stream()
                .collect(Collectors.toMap(NutritionMealPlanItemPo::getId, Function.identity()));
        Map<Long, BigDecimal> confirmedServingsByItemId = new LinkedHashMap<>();
        for (NutritionMealConfirmationItemPo confirmationItem : confirmationItems) {
            if (!Objects.equals(confirmationItem.getFamilyId(), familyId)
                    || !mealItemsById.containsKey(confirmationItem.getMealPlanItemId())) {
                throw new NutritionException(
                        "NUTRITION_CONFIRMATION_ITEM_INVALID", "nutrition confirmation item is invalid");
            }
            if (confirmationItem.isSelected()) {
                confirmedServingsByItemId.merge(confirmationItem.getMealPlanItemId(),
                        confirmationItem.getServingCount(), BigDecimal::add);
            }
        }
        Map<Long, BigDecimal> finalServingsByItemId = mealItems.stream()
                .collect(Collectors.toMap(
                        NutritionMealPlanItemPo::getId,
                        item -> finalServingCount(
                                item, confirmedServingsByItemId.getOrDefault(item.getId(), BigDecimal.ZERO))));

        Map<IngredientKey, IngredientAccumulator> accumulators = new LinkedHashMap<>();
        for (NutritionMealPlanItemPo mealItem : mealItems) {
            if (mealItem.getRecipeId() == null) {
                continue;
            }
            NutritionRecipePo recipe = recipesById.get(mealItem.getRecipeId());
            if (recipe == null) {
                throw new NutritionException("NUTRITION_RECIPE_NOT_FOUND", "nutrition recipe not found");
            }
            BigDecimal effectiveServings = finalServingsByItemId.getOrDefault(mealItem.getId(), BigDecimal.ZERO);
            if (effectiveServings.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal recipeServings = BigDecimal.valueOf(Math.max(recipe.getServingCount(), 1));
            BigDecimal scale = effectiveServings.divide(recipeServings, 6, RoundingMode.HALF_UP);
            List<NutritionRecipeIngredientPo> ingredients = ingredientsByRecipeId
                    .getOrDefault(recipe.getId(), List.of());
            for (NutritionRecipeIngredientPo ingredient : ingredients) {
                BigDecimal plannedAmount = calculationService.ingredientGrams(ingredient)
                        .multiply(scale).setScale(3, RoundingMode.HALF_UP);
                if (plannedAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                NutritionStandardFoodPo food = ingredient.getStandardFoodId() == null
                        ? null
                        : foodsById.get(ingredient.getStandardFoodId());
                String rawFoodName = food == null ? ingredient.getRawFoodName() : food.getNameCn();
                String plannedUnit = "g";
                IngredientKey key = IngredientKey.of(ingredient.getStandardFoodId(), rawFoodName, plannedUnit);
                accumulators.computeIfAbsent(key, ignored -> new IngredientAccumulator(
                                ingredient.getStandardFoodId(), rawFoodName, food == null ? recipe.getCategory() : food.getCategory(),
                                plannedUnit))
                        .add(plannedAmount);
            }
        }

        return new ShoppingDraft(accumulators.values().stream().toList(),
                generatedSnapshot(mealPlan, confirmations, confirmationItems, finalServingsByItemId));
    }

    @Transactional(readOnly = true)
    public ShoppingListResponse getShoppingList(@NotNull Long familyId, @NotNull Long shoppingListId,
                                                Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireReadFamily(userId, familyId);
        NutritionShoppingListPo shoppingList = getShoppingListPo(familyId, shoppingListId);
        return toResponse(shoppingList, shoppingListItemRepository
                .findByShoppingListIdAndDeletedFalseOrderByIdAsc(shoppingList.getId()));
    }

    @Transactional(readOnly = true)
    public List<ShoppingListResponse> listShoppingLists(@NotNull Long familyId, Long mealPlanId, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireReadFamily(userId, familyId);
        List<NutritionShoppingListPo> lists = mealPlanId == null
                ? shoppingListRepository.findByFamilyIdAndDeletedFalseOrderByListDateDescIdDesc(familyId)
                : shoppingListRepository
                .findByFamilyIdAndMealPlanIdAndDeletedFalseOrderByListDateDescIdDesc(familyId, mealPlanId);
        return lists.stream()
                .map(list -> toResponse(list, shoppingListItemRepository
                        .findByShoppingListIdAndDeletedFalseOrderByIdAsc(list.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FoodPriceRecordResponse> listPriceRecords(@NotNull Long familyId, Long standardFoodId,
                                                          Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireReadFamily(userId, familyId);
        List<NutritionFoodPriceRecordPo> records = standardFoodId == null
                ? priceRecordRepository.findTop50ByFamilyIdAndDeletedFalseOrderByPriceDateDescIdDesc(familyId)
                : priceRecordRepository
                .findTop50ByFamilyIdAndStandardFoodIdAndDeletedFalseOrderByPriceDateDescIdDesc(
                        familyId, standardFoodId);
        return records.stream().map(this::toPriceRecordResponse).toList();
    }

    @Transactional
    public ShoppingListResponse transitionShoppingList(@NotNull Long familyId, @NotNull Long shoppingListId,
                                                       @Valid @NotNull TransitionShoppingListRequest request,
                                                       Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireCookFamily(userId, familyId);
        NutritionShoppingListPo shoppingList = getShoppingListPo(familyId, shoppingListId);
        NutritionShoppingListStatus current = shoppingList.getStatus();
        NutritionShoppingListStatus target = request.targetStatus();
        if (current != target && !allowedTargets(current).contains(target)) {
            throw new NutritionException(
                    "NUTRITION_SHOPPING_LIST_STATUS_INVALID", "nutrition shopping list status transition is invalid");
        }
        shoppingList.setStatus(target);
        NutritionShoppingListPo saved = shoppingListRepository.saveAndFlush(shoppingList);
        return toResponse(saved, shoppingListItemRepository
                .findByShoppingListIdAndDeletedFalseOrderByIdAsc(saved.getId()));
    }

    @Transactional
    public ShoppingListItemResponse updateShoppingListItem(@NotNull Long familyId, @NotNull Long shoppingListId,
                                                           @NotNull Long itemId,
                                                           @Valid @NotNull UpdateShoppingListItemRequest request,
                                                           Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireCookFamily(userId, familyId);
        NutritionShoppingListPo shoppingList = getShoppingListPo(familyId, shoppingListId);
        requirePurchasingEditable(shoppingList);
        NutritionShoppingListItemPo item = shoppingListItemRepository
                .findByIdAndFamilyIdAndShoppingListIdAndDeletedFalse(itemId, familyId, shoppingList.getId())
                .orElseThrow(() -> new NutritionException(
                        "NUTRITION_SHOPPING_LIST_ITEM_NOT_FOUND", "nutrition shopping list item not found"));
        applyItemUpdate(item, request);
        NutritionShoppingListItemPo saved = shoppingListItemRepository.saveAndFlush(item);
        recalculateActualTotal(shoppingList);
        return toItemResponse(saved);
    }

    @Transactional
    public FoodPriceRecordResponse createPriceRecord(@NotNull Long familyId,
                                                     @Valid @NotNull CreateFoodPriceRecordRequest request,
                                                     Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireCookFamily(userId, familyId);
        NutritionShoppingListItemPo item = request.shoppingListItemId() == null ? null
                : shoppingListItemRepository.findByIdAndFamilyIdAndDeletedFalse(
                                request.shoppingListItemId(), familyId)
                        .orElseThrow(() -> new NutritionException(
                                "NUTRITION_SHOPPING_LIST_ITEM_NOT_FOUND",
                                "nutrition shopping list item not found"));
        Long standardFoodId = request.standardFoodId() == null && item != null
                ? item.getStandardFoodId()
                : request.standardFoodId();
        String rawFoodName = trimToNull(request.rawFoodName());
        if (rawFoodName == null && item != null) {
            rawFoodName = item.getRawFoodName();
        }
        if (rawFoodName == null) {
            throw new NutritionException("NUTRITION_PRICE_RECORD_FOOD_REQUIRED",
                    "nutrition food price raw food name is required");
        }

        BigDecimal normalizedUnitPrice = normalizedUnitPrice(
                request.totalPrice(), request.specAmount(), request.purchaseQuantity());
        NutritionFoodPriceRecordPo priceRecord = new NutritionFoodPriceRecordPo();
        priceRecord.setFamilyId(familyId);
        priceRecord.setShoppingListItemId(item == null ? null : item.getId());
        priceRecord.setStandardFoodId(standardFoodId);
        priceRecord.setRawFoodName(rawFoodName);
        priceRecord.setPriceDate(request.priceDate() == null ? LocalDate.now() : request.priceDate());
        priceRecord.setChannel(trimToNull(request.channel()));
        priceRecord.setBrand(trimToNull(request.brand()));
        priceRecord.setSpecAmount(request.specAmount());
        priceRecord.setSpecUnit(trimToNull(request.specUnit()));
        priceRecord.setPurchaseQuantity(request.purchaseQuantity());
        priceRecord.setTotalPrice(money(request.totalPrice()));
        priceRecord.setNormalizedUnitPrice(normalizedUnitPrice);
        priceRecord.setSourceType(StringUtils.hasText(request.sourceType())
                ? request.sourceType().trim()
                : item == null ? "MANUAL" : SOURCE_TYPE_SHOPPING_LIST);
        priceRecord.setNote(trimToNull(request.note()));
        NutritionFoodPriceRecordPo saved = priceRecordRepository.saveAndFlush(priceRecord);

        if (item != null) {
            NutritionShoppingListPo shoppingList = getShoppingListPo(familyId, item.getShoppingListId());
            requirePurchasingEditable(shoppingList);
            item.setChannel(priceRecord.getChannel());
            item.setBrand(priceRecord.getBrand());
            item.setSpecAmount(priceRecord.getSpecAmount());
            item.setSpecUnit(priceRecord.getSpecUnit());
            item.setPurchasedAmount(purchasedAmount(priceRecord.getSpecAmount(), priceRecord.getPurchaseQuantity()));
            item.setPurchasedUnit(priceRecord.getSpecUnit());
            item.setTotalPrice(priceRecord.getTotalPrice());
            item.setNormalizedUnitPrice(priceRecord.getNormalizedUnitPrice());
            item.setItemStatus(ITEM_STATUS_PURCHASED);
            item.setNote(priceRecord.getNote());
            item.setMetadataJson(writeDecimal(item.getMetadataJson(), "purchaseQuantity",
                    priceRecord.getPurchaseQuantity()));
            shoppingListItemRepository.saveAndFlush(item);
            recalculateActualTotal(shoppingList);
        }
        return toPriceRecordResponse(saved);
    }

    Optional<NutritionShoppingListPo> findShoppingListPo(Long familyId, Long shoppingListId) {
        return shoppingListRepository.findByIdAndFamilyIdAndDeletedFalse(shoppingListId, familyId);
    }

    ShoppingListResponse toResponse(NutritionShoppingListPo shoppingList, List<NutritionShoppingListItemPo> items) {
        return new ShoppingListResponse(shoppingList.getId(), shoppingList.getFamilyId(),
                shoppingList.getMealPlanId(), shoppingList.getListDate(), shoppingList.getStatus(),
                shoppingList.getTitle(), shoppingList.getEstimatedTotalPrice(), shoppingList.getActualTotalPrice(),
                shoppingList.getGeneratedSnapshot(),
                items.stream().map(this::toItemResponse).toList(),
                shoppingList.getCreatedAt(), shoppingList.getUpdatedAt());
    }

    private NutritionMealPlanPo getLockedMealPlan(Long familyId, Long mealPlanId) {
        return mealPlanRepository.findLockedByIdAndFamilyIdAndDeletedFalse(mealPlanId, familyId)
                .orElseThrow(() -> new NutritionException(
                        "NUTRITION_MEAL_PLAN_NOT_FOUND", "nutrition meal plan not found"));
    }

    private NutritionException invalidMealPlanStatus() {
        return new NutritionException(
                "NUTRITION_MEAL_PLAN_STATUS_INVALID", "nutrition meal plan status transition is invalid");
    }

    private void validateRecipesVisible(Long familyId, List<Long> recipeIds, Map<Long, NutritionRecipePo> recipesById) {
        for (Long recipeId : recipeIds) {
            NutritionRecipePo recipe = recipesById.get(recipeId);
            if (recipe == null || !isRecipeVisibleToFamily(familyId, recipe)) {
                throw new NutritionException("NUTRITION_RECIPE_NOT_FOUND", "nutrition recipe not found");
            }
        }
    }

    private boolean isRecipeVisibleToFamily(Long familyId, NutritionRecipePo recipe) {
        return NutritionRecipeSourceType.PLATFORM_PUBLIC == recipe.getSourceType()
                || Objects.equals(recipe.getFamilyId(), familyId);
    }

    private NutritionShoppingListPo getShoppingListPo(Long familyId, Long shoppingListId) {
        return findShoppingListPo(familyId, shoppingListId)
                .orElseThrow(() -> new NutritionException(
                        "NUTRITION_SHOPPING_LIST_NOT_FOUND", "nutrition shopping list not found"));
    }

    private Set<NutritionShoppingListStatus> allowedTargets(NutritionShoppingListStatus current) {
        return switch (current) {
            case DRAFT -> EnumSet.of(NutritionShoppingListStatus.ACTIVE, NutritionShoppingListStatus.CANCELLED);
            case ACTIVE -> EnumSet.of(NutritionShoppingListStatus.PURCHASING,
                    NutritionShoppingListStatus.CANCELLED);
            case PURCHASING -> EnumSet.of(NutritionShoppingListStatus.PURCHASED,
                    NutritionShoppingListStatus.CANCELLED);
            case PURCHASED -> EnumSet.of(NutritionShoppingListStatus.CLOSED);
            case CLOSED, CANCELLED -> EnumSet.noneOf(NutritionShoppingListStatus.class);
        };
    }

    private void requirePurchasingEditable(NutritionShoppingListPo shoppingList) {
        if (shoppingList.getStatus() != NutritionShoppingListStatus.ACTIVE
                && shoppingList.getStatus() != NutritionShoppingListStatus.PURCHASING) {
            throw new NutritionException(
                    "NUTRITION_SHOPPING_LIST_STATUS_INVALID", "nutrition shopping list is not editable");
        }
    }

    private boolean shoppingStarted(NutritionShoppingListPo shoppingList) {
        return shoppingList.getStatus() == NutritionShoppingListStatus.PURCHASING
                || shoppingList.getStatus() == NutritionShoppingListStatus.PURCHASED
                || shoppingList.getStatus() == NutritionShoppingListStatus.CLOSED;
    }

    private BigDecimal finalServingCount(NutritionMealPlanItemPo item, BigDecimal confirmedServingCount) {
        BigDecimal adjusted = readDecimal(item.getMetadataJson(), "finalServingCount");
        return adjusted == null ? confirmedServingCount : adjusted;
    }

    private NutritionShoppingListItemPo createShoppingListItem(Long familyId, Long shoppingListId,
                                                               IngredientAccumulator accumulator) {
        NutritionShoppingListItemPo item = new NutritionShoppingListItemPo();
        item.setFamilyId(familyId);
        item.setShoppingListId(shoppingListId);
        item.setStandardFoodId(accumulator.standardFoodId());
        item.setRawFoodName(accumulator.rawFoodName());
        item.setCategory(accumulator.category());
        item.setPlannedAmount(amount(accumulator.plannedAmount()));
        item.setPlannedUnit(accumulator.plannedUnit());
        item.setItemStatus(ITEM_STATUS_PLANNED);
        estimateIngredientTotal(familyId, accumulator)
                .ifPresent(estimated -> item.setMetadataJson(writeDecimal(
                        item.getMetadataJson(), "estimatedTotalPrice", estimated)));
        return item;
    }

    private Optional<BigDecimal> estimateIngredientTotal(Long familyId, IngredientAccumulator accumulator) {
        Optional<NutritionFoodPriceRecordPo> latest = accumulator.standardFoodId() == null
                ? priceRecordRepository
                .findFirstByFamilyIdAndRawFoodNameIgnoreCaseAndSpecUnitIgnoreCaseAndDeletedFalseOrderByPriceDateDescIdDesc(
                        familyId, accumulator.rawFoodName(), accumulator.plannedUnit())
                : priceRecordRepository
                .findFirstByFamilyIdAndStandardFoodIdAndSpecUnitIgnoreCaseAndDeletedFalseOrderByPriceDateDescIdDesc(
                        familyId, accumulator.standardFoodId(), accumulator.plannedUnit());
        return latest.map(NutritionFoodPriceRecordPo::getNormalizedUnitPrice)
                .filter(Objects::nonNull)
                .map(unitPrice -> money(unitPrice.multiply(accumulator.plannedAmount())));
    }

    private BigDecimal estimatedTotal(List<NutritionShoppingListItemPo> items) {
        BigDecimal total = items.stream()
                .map(NutritionShoppingListItemPo::getMetadataJson)
                .map(metadata -> readDecimal(metadata, "estimatedTotalPrice"))
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.compareTo(BigDecimal.ZERO) > 0 ? money(total) : null;
    }

    private Map<Long, NutritionStandardFoodPo> standardFoods(List<Long> standardFoodIds) {
        if (standardFoodIds.isEmpty()) {
            return Map.of();
        }
        return standardFoodRepository.findByIdInAndStatusAndDeletedFalse(
                        standardFoodIds, NutritionStatus.ACTIVE)
                .stream()
                .collect(Collectors.toMap(NutritionStandardFoodPo::getId, Function.identity()));
    }

    private void applyItemUpdate(NutritionShoppingListItemPo item, UpdateShoppingListItemRequest request) {
        if (request.purchasedAmount() != null) {
            item.setPurchasedAmount(amount(request.purchasedAmount()));
        }
        if (request.purchasedUnit() != null) {
            item.setPurchasedUnit(trimToNull(request.purchasedUnit()));
        }
        if (request.channel() != null) {
            item.setChannel(trimToNull(request.channel()));
        }
        if (request.brand() != null) {
            item.setBrand(trimToNull(request.brand()));
        }
        if (request.specAmount() != null) {
            item.setSpecAmount(amount(request.specAmount()));
        }
        if (request.specUnit() != null) {
            item.setSpecUnit(trimToNull(request.specUnit()));
        }
        if (request.totalPrice() != null) {
            item.setTotalPrice(money(request.totalPrice()));
        }
        if (request.note() != null) {
            item.setNote(trimToNull(request.note()));
        }
        if (request.purchaseQuantity() != null) {
            item.setMetadataJson(writeDecimal(item.getMetadataJson(), "purchaseQuantity",
                    amount(request.purchaseQuantity())));
        }
        if (request.checked() != null) {
            item.setItemStatus(Boolean.TRUE.equals(request.checked()) ? ITEM_STATUS_CHECKED : ITEM_STATUS_PLANNED);
        }
        if (StringUtils.hasText(request.itemStatus())) {
            item.setItemStatus(request.itemStatus().trim());
        }
        BigDecimal purchaseQuantity = request.purchaseQuantity() == null
                ? readDecimal(item.getMetadataJson(), "purchaseQuantity")
                : request.purchaseQuantity();
        item.setNormalizedUnitPrice(normalizedUnitPrice(item.getTotalPrice(), item.getSpecAmount(), purchaseQuantity));
    }

    private void recalculateActualTotal(NutritionShoppingListPo shoppingList) {
        List<NutritionShoppingListItemPo> items = shoppingListItemRepository
                .findByShoppingListIdAndDeletedFalseOrderByIdAsc(shoppingList.getId());
        BigDecimal total = items.stream()
                .map(NutritionShoppingListItemPo::getTotalPrice)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        shoppingList.setActualTotalPrice(total.compareTo(BigDecimal.ZERO) > 0 ? money(total) : null);
        shoppingListRepository.saveAndFlush(shoppingList);
    }

    private ShoppingListItemResponse toItemResponse(NutritionShoppingListItemPo item) {
        return new ShoppingListItemResponse(item.getId(), item.getShoppingListId(), item.getStandardFoodId(),
                item.getRawFoodName(), item.getCategory(), item.getPlannedAmount(), item.getPlannedUnit(),
                item.getPurchasedAmount(), item.getPurchasedUnit(), item.getChannel(), item.getBrand(),
                item.getSpecAmount(), item.getSpecUnit(), readDecimal(item.getMetadataJson(), "purchaseQuantity"),
                item.getTotalPrice(), item.getNormalizedUnitPrice(), item.getItemStatus(), item.getNote(),
                item.getCreatedAt(), item.getUpdatedAt());
    }

    private FoodPriceRecordResponse toPriceRecordResponse(NutritionFoodPriceRecordPo priceRecord) {
        return new FoodPriceRecordResponse(priceRecord.getId(), priceRecord.getFamilyId(),
                priceRecord.getShoppingListItemId(), priceRecord.getStandardFoodId(), priceRecord.getRawFoodName(),
                priceRecord.getPriceDate(), priceRecord.getChannel(), priceRecord.getBrand(),
                priceRecord.getSpecAmount(), priceRecord.getSpecUnit(), priceRecord.getPurchaseQuantity(),
                priceRecord.getTotalPrice(), priceRecord.getNormalizedUnitPrice(), priceRecord.getSourceType(),
                priceRecord.getNote(), priceRecord.getCreatedAt(), priceRecord.getUpdatedAt());
    }

    private String generatedSnapshot(NutritionMealPlanPo mealPlan,
                                     List<NutritionMealConfirmationPo> confirmations,
                                     List<NutritionMealConfirmationItemPo> confirmationItems,
                                     Map<Long, BigDecimal> finalServingsByItemId) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("mealPlanId", mealPlan.getId());
        node.put("mealPlanVersion", mealPlan.getVersion());
        ArrayNode finalMenuNodes = node.putArray("finalMenu");
        finalServingsByItemId.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    ObjectNode itemNode = finalMenuNodes.addObject();
                    itemNode.put("mealPlanItemId", entry.getKey());
                    itemNode.put("servingCount", entry.getValue());
                });
        ArrayNode confirmationNodes = node.putArray("confirmations");
        for (NutritionMealConfirmationPo confirmation : confirmations) {
            ObjectNode confirmationNode = confirmationNodes.addObject();
            confirmationNode.put("id", confirmation.getId());
            confirmationNode.put("version", confirmation.getVersion());
            ArrayNode itemNodes = confirmationNode.putArray("items");
            confirmationItems.stream()
                    .filter(item -> Objects.equals(item.getConfirmationId(), confirmation.getId()))
                    .sorted(Comparator.comparing(NutritionMealConfirmationItemPo::getId))
                    .forEach(item -> {
                        ObjectNode itemNode = itemNodes.addObject();
                        itemNode.put("id", item.getId());
                        itemNode.put("version", item.getVersion());
                        itemNode.put("mealPlanItemId", item.getMealPlanItemId());
                        itemNode.put("selected", item.isSelected());
                        itemNode.put("servingCount", item.getServingCount());
                    });
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new NutritionException("NUTRITION_JSON_INVALID", "nutrition shopping list snapshot is invalid");
        }
    }

    private String writeDecimal(String metadataJson, String field, BigDecimal value) {
        ObjectNode node = metadata(metadataJson);
        if (value == null) {
            node.remove(field);
        } else {
            node.put(field, value);
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new NutritionException("NUTRITION_JSON_INVALID", "nutrition shopping item metadata is invalid");
        }
    }

    private BigDecimal readDecimal(String metadataJson, String field) {
        JsonNode value = metadata(metadataJson).get(field);
        return value == null || !value.isNumber() ? null : value.decimalValue();
    }

    private ObjectNode metadata(String metadataJson) {
        if (!StringUtils.hasText(metadataJson)) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode node = objectMapper.readTree(metadataJson);
            return node instanceof ObjectNode objectNode ? objectNode : objectMapper.createObjectNode();
        } catch (JsonProcessingException e) {
            throw new NutritionException("NUTRITION_JSON_INVALID", "nutrition shopping item metadata is invalid");
        }
    }

    private BigDecimal normalizedUnitPrice(BigDecimal totalPrice, BigDecimal specAmount, BigDecimal purchaseQuantity) {
        if (totalPrice == null || specAmount == null || purchaseQuantity == null
                || specAmount.compareTo(BigDecimal.ZERO) <= 0
                || purchaseQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return totalPrice.divide(specAmount.multiply(purchaseQuantity), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal purchasedAmount(BigDecimal specAmount, BigDecimal purchaseQuantity) {
        if (specAmount == null || purchaseQuantity == null) {
            return null;
        }
        return amount(specAmount.multiply(purchaseQuantity));
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private BigDecimal amount(BigDecimal value) {
        return value == null ? null : value.setScale(3, RoundingMode.HALF_UP);
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private Long requireActor(Long actorId) {
        if (actorId == null || actorId <= 0) {
            throw new NutritionException("NUTRITION_FORBIDDEN", "Nutrition family access is required");
        }
        return actorId;
    }

    private record ShoppingDraft(
            List<IngredientAccumulator> accumulators,
            String generatedSnapshot
    ) {
    }

    private record IngredientKey(Long standardFoodId, String rawFoodName, String plannedUnit) {

        static IngredientKey of(Long standardFoodId, String rawFoodName, String plannedUnit) {
            if (standardFoodId != null) {
                return new IngredientKey(standardFoodId, null, plannedUnit);
            }
            return new IngredientKey(null, StringUtils.hasText(rawFoodName)
                    ? rawFoodName.trim().toLowerCase()
                    : "", plannedUnit);
        }
    }

    private static final class IngredientAccumulator {

        private final Long standardFoodId;
        private final String rawFoodName;
        private final String category;
        private final String plannedUnit;
        private BigDecimal plannedAmount = BigDecimal.ZERO;

        private IngredientAccumulator(Long standardFoodId, String rawFoodName, String category, String plannedUnit) {
            this.standardFoodId = standardFoodId;
            this.rawFoodName = rawFoodName;
            this.category = category;
            this.plannedUnit = plannedUnit;
        }

        private void add(BigDecimal amount) {
            plannedAmount = plannedAmount.add(amount);
        }

        private Long standardFoodId() {
            return standardFoodId;
        }

        private String rawFoodName() {
            return rawFoodName;
        }

        private String category() {
            return category;
        }

        private String plannedUnit() {
            return plannedUnit;
        }

        private BigDecimal plannedAmount() {
            return plannedAmount;
        }
    }
}
