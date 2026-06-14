package top.egon.mario.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import top.egon.mario.rag.dto.response.RagSearchMode;

import java.math.BigDecimal;

/**
 * Externalized RAG settings for storage, retrieval and model defaults.
 */
@ConfigurationProperties(prefix = "mario.rag")
public record RagProperties(
        Storage storage,
        Retrieval retrieval,
        Model model
) {

    public RagProperties {
        storage = storage == null ? new Storage("LOCAL", System.getProperty("user.home") + "/.cyber-mario/rag-files") : storage;
        retrieval = retrieval == null ? new Retrieval(6, BigDecimal.valueOf(0.55), RagSearchMode.HYBRID, 50, 6,
                BigDecimal.valueOf(0.65), BigDecimal.valueOf(0.35), 800, 120, false, "qwen3-rerank",
                "https://dashscope.aliyuncs.com/compatible-api/v1") : retrieval;
        model = model == null ? new Model("qwen-plus", "text-embedding-v4", 1024) : model;
    }

    public record Storage(
            String type,
            String localRoot
    ) {
    }

    public record Retrieval(
            int defaultTopK,
            BigDecimal defaultSimilarityThreshold,
            RagSearchMode defaultSearchMode,
            int candidateTopK,
            int contextTopK,
            BigDecimal vectorWeight,
            BigDecimal keywordWeight,
            int chunkSize,
            int chunkOverlap,
            boolean rerankEnabled,
            String rerankModel,
            String rerankBaseUrl
    ) {
        public Retrieval {
            defaultSearchMode = defaultSearchMode == null ? RagSearchMode.HYBRID : defaultSearchMode;
            candidateTopK = candidateTopK <= 0 ? 50 : candidateTopK;
            contextTopK = contextTopK <= 0 ? 6 : contextTopK;
            vectorWeight = vectorWeight == null ? BigDecimal.valueOf(0.65) : vectorWeight;
            keywordWeight = keywordWeight == null ? BigDecimal.valueOf(0.35) : keywordWeight;
            chunkSize = chunkSize <= 0 ? 800 : chunkSize;
            chunkOverlap = chunkOverlap <= 0 ? 120 : chunkOverlap;
            rerankModel = rerankModel == null || rerankModel.isBlank() ? "qwen3-rerank" : rerankModel;
            rerankBaseUrl = rerankBaseUrl == null || rerankBaseUrl.isBlank()
                    ? "https://dashscope.aliyuncs.com/compatible-api/v1"
                    : rerankBaseUrl;
        }
    }

    public record Model(
            String chatModel,
            String embeddingModel,
            int embeddingDimension
    ) {
    }

}
