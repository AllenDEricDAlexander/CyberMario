package top.egon.mario.nutrition.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for an ordered recipe cooking step.
 */
public record RecipeStepRequest(
        @NotNull @Min(1) Integer stepNo,
        @Size(max = 128) String title,
        @NotBlank @Size(max = 4096) String instruction
) {
}
