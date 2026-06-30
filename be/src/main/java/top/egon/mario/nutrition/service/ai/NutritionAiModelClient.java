package top.egon.mario.nutrition.service.ai;

/**
 * Boundary for AI menu generation so tests can use deterministic model output.
 */
public interface NutritionAiModelClient {

    String generateMenu(NutritionAiModelRequest request);
}
