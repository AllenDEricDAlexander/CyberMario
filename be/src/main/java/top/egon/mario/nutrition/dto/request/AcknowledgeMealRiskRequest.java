package top.egon.mario.nutrition.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Cook acknowledgement for active medium meal-plan risks.
 */
public record AcknowledgeMealRiskRequest(
        @NotEmpty List<@Min(1) Long> riskIds,
        @NotBlank @Size(max = 512) String note
) {

    public AcknowledgeMealRiskRequest {
        riskIds = riskIds == null ? List.of() : List.copyOf(riskIds);
    }
}
