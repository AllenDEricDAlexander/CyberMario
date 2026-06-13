package top.egon.mario.rag.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for importing plain text into a RAG knowledge base.
 */
public record ImportTextDocumentRequest(
        @NotNull Long knowledgeBaseId,
        @NotBlank @Size(max = 255) String title,
        @NotBlank String content,
        Boolean parseImmediately
) {
}
