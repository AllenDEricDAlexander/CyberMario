package top.egon.mario.agent.externalim;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.agent.externalim.guard.ChatGuardDecision;
import top.egon.mario.agent.externalim.guard.po.AgentChatGuardAuditPo;
import top.egon.mario.agent.externalim.guard.repository.AgentChatGuardAuditRepository;
import top.egon.mario.agent.externalim.memory.po.AgentMemorySpacePo;
import top.egon.mario.agent.externalim.memory.po.ExternalChatBindingPo;
import top.egon.mario.agent.externalim.memory.po.enums.AgentMemorySpaceStatus;
import top.egon.mario.agent.externalim.memory.repository.AgentMemorySpaceRepository;
import top.egon.mario.agent.externalim.memory.repository.ExternalChatBindingRepository;
import top.egon.mario.agent.externalim.model.ChatSource;
import top.egon.mario.agent.externalim.model.ExternalChatPlatform;
import top.egon.mario.agent.externalim.model.ExternalConversationType;
import top.egon.mario.agent.externalim.runtime.po.ExternalChatEventPo;
import top.egon.mario.agent.externalim.runtime.po.enums.ExternalChatProcessingStatus;
import top.egon.mario.agent.externalim.runtime.po.enums.ExternalChatReplyStatus;
import top.egon.mario.agent.externalim.runtime.repository.ExternalChatEventRepository;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.ai.dashscope.api-key=test-api-key",
        "mario.agent.external-im.worker.enabled=false",
        "mario.agent.external-im.telegram.enabled=false"
})
@Transactional
class ExternalImPersistenceMappingTests {

    @Autowired
    private AgentMemorySpaceRepository spaceRepository;

    @Autowired
    private ExternalChatBindingRepository bindingRepository;

    @Autowired
    private ExternalChatEventRepository eventRepository;

    @Autowired
    private AgentChatGuardAuditRepository guardAuditRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayAndJpaRoundTripTheExternalChatBoundary() {
        Instant now = Instant.parse("2026-07-20T00:00:00Z");
        AgentMemorySpacePo space = new AgentMemorySpacePo();
        space.setSpaceId("space-1");
        space.setOwnerUserId(8L);
        space.setName("Shared agent");
        space.setStatus(AgentMemorySpaceStatus.ACTIVE);
        space.setCreatedAt(now);
        space.setUpdatedAt(now);
        spaceRepository.saveAndFlush(space);

        ExternalChatBindingPo binding = new ExternalChatBindingPo();
        binding.setSpaceId("space-1");
        binding.setPlatform(ExternalChatPlatform.TELEGRAM);
        binding.setConnectorId("main");
        binding.setExternalConversationId("-1001");
        binding.setConversationType(ExternalConversationType.GROUP);
        binding.setAudienceKey("telegram:main:-1001");
        binding.setEnabled(true);
        binding.setCreatedAt(now);
        binding.setUpdatedAt(now);
        bindingRepository.saveAndFlush(binding);

        ExternalChatEventPo event = new ExternalChatEventPo();
        event.setPlatform(ExternalChatPlatform.TELEGRAM);
        event.setConnectorId("main");
        event.setExternalEventId("update-1");
        event.setExternalMessageId("77");
        event.setSpaceId("space-1");
        event.setOwnerUserId(8L);
        event.setNormalizedMessageJson("{\"eventId\":\"update-1\"}");
        event.setProcessingStatus(ExternalChatProcessingStatus.RECEIVED);
        event.setReplyStatus(ExternalChatReplyStatus.NOT_REQUIRED);
        event.setAvailableAt(now);
        event.setRequestId("request-1");
        event.setTraceId("trace-1");
        event.setReceivedAt(now);
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        event = eventRepository.saveAndFlush(event);

        AgentChatGuardAuditPo audit = new AgentChatGuardAuditPo();
        audit.setOwnerUserId(8L);
        audit.setChatSource(ChatSource.EXTERNAL_IM);
        audit.setMemorySpaceId("space-1");
        audit.setPlatform(ExternalChatPlatform.TELEGRAM);
        audit.setConnectorId("main");
        audit.setConversationId("-1001");
        audit.setConversationType(ExternalConversationType.GROUP);
        audit.setAudienceKey("telegram:main:-1001");
        audit.setDecision(ChatGuardDecision.IGNORE);
        audit.setConfidence(BigDecimal.ZERO);
        audit.setReason("ambient group message");
        audit.setDurationMs(5L);
        audit.setRequestId("request-1");
        audit.setTraceId("trace-1");
        audit.setExternalEventId("update-1");
        audit.setCreatedAt(now);
        audit = guardAuditRepository.saveAndFlush(audit);

        entityManager.flush();
        entityManager.clear();

        assertThat(spaceRepository.findBySpaceIdAndDeletedFalse("space-1")).get()
                .extracting(AgentMemorySpacePo::getOwnerUserId).isEqualTo(8L);
        assertThat(bindingRepository
                .findByPlatformAndConnectorIdAndExternalConversationIdAndEnabledTrueAndDeletedFalse(
                        ExternalChatPlatform.TELEGRAM, "main", "-1001")).get()
                .extracting(ExternalChatBindingPo::getSpaceId).isEqualTo("space-1");
        assertThat(eventRepository.findById(event.getId())).get()
                .extracting(ExternalChatEventPo::getProcessingStatus)
                .isEqualTo(ExternalChatProcessingStatus.RECEIVED);
        assertThat(guardAuditRepository.findById(audit.getId())).get()
                .extracting(AgentChatGuardAuditPo::getDecision)
                .isEqualTo(ChatGuardDecision.IGNORE);
        assertThat(jdbcTemplate.queryForList("""
                select column_name
                from information_schema.columns
                where table_name in (
                    'agent_memory_session',
                    'agent_memory_message',
                    'agent_long_term_memory'
                )
                """, String.class))
                .contains("memory_domain", "memory_space_id",
                        "external_event_id", "scope_key");
    }
}
