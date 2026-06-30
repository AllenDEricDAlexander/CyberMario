package top.egon.mario.nutrition.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for creating a nutrition family.
 */
public record CreateFamilyRequest(
        @NotBlank @Size(min = 2, max = 128) String name,
        @Size(max = 128) String region,
        @Size(max = 16) String currency,
        List<@NotBlank @Size(max = 32) String> defaultMealTypes,
        @Size(max = 128) String ownerNickname
) {
}
