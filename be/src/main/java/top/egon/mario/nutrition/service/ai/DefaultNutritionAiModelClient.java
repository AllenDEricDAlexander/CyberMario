package top.egon.mario.nutrition.service.ai;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.egon.mario.agent.model.dto.enums.ModelProviderType;
import top.egon.mario.agent.model.dto.enums.ModelScenario;
import top.egon.mario.agent.model.dto.request.ModelOptions;
import top.egon.mario.agent.model.dto.request.ModelRequest;
import top.egon.mario.agent.model.dto.response.ModelResolveResult;
import top.egon.mario.agent.model.service.MarioModelFactory;
import top.egon.mario.agent.model.service.model.ModelCallContext;
import top.egon.mario.nutrition.service.NutritionException;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Production AI model adapter for nutrition menu drafts.
 */
@Service
public class DefaultNutritionAiModelClient implements NutritionAiModelClient {

    private final MarioModelFactory modelFactory;
    private final String modelName;

    public DefaultNutritionAiModelClient(MarioModelFactory modelFactory,
                                         @Value("${mario.nutrition.ai.model:qwen3.7-plus}") String modelName) {
        this.modelFactory = modelFactory;
        this.modelName = modelName;
    }

    @Override
    public String generateMenu(NutritionAiModelRequest request) {
        ModelResolveResult result = modelFactory.resolve(new ModelRequest(
                ModelProviderType.DASHSCOPE,
                modelName,
                new ModelOptions(BigDecimal.valueOf(0.2), 2048,
                        null, null, false, null, false, true, Map.of()),
                new ModelCallContext(request.requestedBy(), null, null, "nutrition-ai-job-" + request.jobId(),
                        ModelScenario.BACKGROUND_TASK, String.valueOf(request.jobId()), null, null)
        ));
        String responseText = responseText(result.chatModel().call(new Prompt(
                new SystemMessage(systemPrompt()),
                new UserMessage(userPrompt(request))
        )));
        if (!StringUtils.hasText(responseText)) {
            throw new NutritionException("NUTRITION_AI_EMPTY_OUTPUT", "AI recommendation output is empty");
        }
        return responseText.trim();
    }

    private String systemPrompt() {
        return """
                You create meal-plan candidates for a family nutrition application.
                Return only one strict JSON object and no markdown, code fences, commentary, or extra text.
                JSON shape: {"title":string,"reason":string,"mealTypes":["DINNER"],"recipes":[{"mealType":"DINNER","name":string,"servingCount":number,"reason":string}],"costEstimate":number}
                Meal plans are drafts only. Never claim that a plan is published, confirmed, or final.
                Keep the menu practical and reviewable by a human family manager.
                """;
    }

    private String userPrompt(NutritionAiModelRequest request) {
        return """
                Family id: %s
                Family name: %s
                Planned date: %s
                Target meal types: %s

                Input snapshot:
                <input_snapshot>
                %s
                </input_snapshot>
                """.formatted(request.familyId(), safe(request.familyName()), request.plannedDate(),
                request.mealTypes(), safe(request.inputSnapshot()));
    }

    private String responseText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        return response.getResult().getOutput().getText();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
