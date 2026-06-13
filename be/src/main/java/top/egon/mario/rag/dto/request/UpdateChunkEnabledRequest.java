package top.egon.mario.rag.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for enabling or disabling a RAG chunk.
 */
public record UpdateChunkEnabledRequest(
        @NotNull Boolean enabled
) {
}
