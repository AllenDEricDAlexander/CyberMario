package top.egon.mario.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
        retrieval = retrieval == null ? new Retrieval(6, BigDecimal.valueOf(0.55)) : retrieval;
        model = model == null ? new Model("qwen-plus", "text-embedding-v4", 1024) : model;
    }

    public record Storage(
            String type,
            String localRoot
    ) {
    }

    public record Retrieval(
            int defaultTopK,
            BigDecimal defaultSimilarityThreshold
    ) {
    }

    public record Model(
            String chatModel,
            String embeddingModel,
            int embeddingDimension
    ) {
    }

}
