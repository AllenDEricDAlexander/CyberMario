package top.egon.mario.rag.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import top.egon.mario.rag.dto.response.SourceReferenceResponse;

import java.util.Collection;
import java.util.List;

/**
 * Keyword retrieval boundary for hybrid RAG.
 */
public interface RagKeywordSearchService {

    List<SourceReferenceResponse> search(@NotBlank String query, @NotNull Collection<Long> knowledgeBaseIds, int topK);

}
