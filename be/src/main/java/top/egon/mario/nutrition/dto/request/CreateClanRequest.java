package top.egon.mario.nutrition.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a nutrition clan.
 */
public record CreateClanRequest(
        @NotBlank @Size(min = 2, max = 128) String name
) {
}
