package top.egon.mario.nutrition.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating or updating a nutrition health tag.
 */
public record UpsertHealthTagRequest(
        @NotBlank @Size(max = 64) String tagType,
        @NotBlank @Size(max = 128) String tagCode,
        @NotBlank @Size(max = 128) String name,
        @Size(max = 512) String description,
        @NotNull @Min(0) Integer sortOrder
) {
}
