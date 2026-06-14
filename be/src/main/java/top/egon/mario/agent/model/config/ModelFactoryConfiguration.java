package top.egon.mario.agent.model.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.egon.mario.agent.model.service.MarioModelFactory;
import top.egon.mario.agent.model.service.ModelAuditService;
import top.egon.mario.agent.model.service.impl.DefaultMarioModelFactory;
import top.egon.mario.agent.model.service.provider.ModelProviderAdapter;

import java.util.List;

/**
 * Wires the registry-backed model factory used by chat and background tasks.
 */
@Configuration
public class ModelFactoryConfiguration {

    /**
     * Creates the registry-backed model factory from all provider adapters.
     */
    @Bean
    public MarioModelFactory marioModelFactory(List<ModelProviderAdapter> modelProviderAdapters,
                                               ModelAuditService modelAuditService) {
        return new DefaultMarioModelFactory(modelProviderAdapters, modelAuditService);
    }

    @Bean
    public DashScopeApi dashScopeApi(@Value("${spring.ai.dashscope.api-key:${AI_DASHSCOPE_API_KEY:}}") String apiKey) {
        return DashScopeApi.builder().apiKey(apiKey).build();
    }

}
