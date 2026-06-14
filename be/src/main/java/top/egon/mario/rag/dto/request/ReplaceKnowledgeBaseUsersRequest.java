package top.egon.mario.rag.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import top.egon.mario.rag.po.enums.RagAccessLevel;

import java.util.List;

/**
 * Request body for replacing user grants on a RAG knowledge base.
 */
public record ReplaceKnowledgeBaseUsersRequest(
        @NotEmpty List<@Valid Grant> users
) {

    public record Grant(
            @NotNull Long userId,
            @NotNull RagAccessLevel accessLevel
    ) {
    }

}
