package top.egon.mario.rag.dto.response;

import top.egon.mario.rag.po.enums.RagAccessLevel;

/**
 * User grant response for a RAG knowledge base.
 */
public record KnowledgeBaseUserResponse(
        Long id,
        Long knowledgeBaseId,
        Long userId,
        RagAccessLevel accessLevel
) {
}
