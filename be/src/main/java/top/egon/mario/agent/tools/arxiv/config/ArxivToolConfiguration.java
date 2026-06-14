package top.egon.mario.agent.tools.arxiv.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enables arXiv tool configuration properties.
 */
@Configuration
@EnableConfigurationProperties(ArxivToolProperties.class)
public class ArxivToolConfiguration {
}
