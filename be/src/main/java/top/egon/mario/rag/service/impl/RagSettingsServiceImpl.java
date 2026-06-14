package top.egon.mario.rag.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.egon.mario.rag.config.RagProperties;
import top.egon.mario.rag.dto.response.RagSettingsResponse;
import top.egon.mario.rag.service.RagSettingsService;

/**
 * Default read-only RAG settings service.
 */
@Service
@RequiredArgsConstructor
public class RagSettingsServiceImpl implements RagSettingsService {

    private final RagProperties properties;

    @Override
    public RagSettingsResponse settings() {
        return new RagSettingsResponse(
                properties.model().chatModel(),
                properties.model().embeddingModel(),
                properties.model().embeddingDimension(),
                properties.retrieval().defaultSearchMode(),
                properties.retrieval().defaultTopK(),
                properties.retrieval().candidateTopK(),
                properties.retrieval().contextTopK(),
                properties.retrieval().defaultSimilarityThreshold(),
                properties.retrieval().rerankEnabled(),
                properties.retrieval().rerankModel()
        );
    }

}
