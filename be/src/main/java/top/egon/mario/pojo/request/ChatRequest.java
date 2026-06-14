package top.egon.mario.pojo.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for an agent conversation turn.
 */
public record ChatRequest(@NotBlank String message, String threadId) {
}
