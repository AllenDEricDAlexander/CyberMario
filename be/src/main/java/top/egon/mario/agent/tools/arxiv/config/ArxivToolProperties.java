package top.egon.mario.agent.tools.arxiv.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized settings for the arXiv agent tool.
 */
@ConfigurationProperties(prefix = "mario.agent.arxiv")
public record ArxivToolProperties(
        int defaultMaxResults,
        int maxResults,
        int fullTextPreviewChars
) {

    public ArxivToolProperties {
        defaultMaxResults = defaultMaxResults <= 0 ? 5 : defaultMaxResults;
        maxResults = maxResults <= 0 ? 10 : maxResults;
        fullTextPreviewChars = fullTextPreviewChars <= 0 ? 12_000 : fullTextPreviewChars;
    }

}
