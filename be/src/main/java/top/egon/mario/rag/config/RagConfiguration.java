package top.egon.mario.rag.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enables RAG module configuration properties.
 */
@Configuration
@EnableConfigurationProperties(RagProperties.class)
public class RagConfiguration {
}
