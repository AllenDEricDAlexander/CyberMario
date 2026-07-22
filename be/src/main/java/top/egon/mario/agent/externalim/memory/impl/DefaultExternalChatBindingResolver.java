package top.egon.mario.agent.externalim.memory.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.agent.externalim.ExternalChatException;
import top.egon.mario.agent.externalim.memory.ExternalChatBindingResolver;
import top.egon.mario.agent.externalim.memory.model.ResolvedExternalChatBinding;
import top.egon.mario.agent.externalim.memory.po.AgentMemorySpacePo;
import top.egon.mario.agent.externalim.memory.po.ExternalChatBindingPo;
import top.egon.mario.agent.externalim.memory.po.enums.AgentMemorySpaceStatus;
import top.egon.mario.agent.externalim.memory.repository.AgentMemorySpaceRepository;
import top.egon.mario.agent.externalim.memory.repository.ExternalChatBindingRepository;
import top.egon.mario.agent.externalim.model.ExternalChatMessage;

@Service
public class DefaultExternalChatBindingResolver implements ExternalChatBindingResolver {

    private final ExternalChatBindingRepository bindingRepository;
    private final AgentMemorySpaceRepository spaceRepository;

    public DefaultExternalChatBindingResolver(ExternalChatBindingRepository bindingRepository,
                                              AgentMemorySpaceRepository spaceRepository) {
        this.bindingRepository = bindingRepository;
        this.spaceRepository = spaceRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public ResolvedExternalChatBinding resolve(ExternalChatMessage message) {
        if (message == null || message.platform() == null
                || !StringUtils.hasText(message.connectorId())
                || !StringUtils.hasText(message.conversationId())) {
            throw new ExternalChatException("EXTERNAL_CHAT_MESSAGE_INVALID",
                    "normalized external chat message is invalid");
        }
        ExternalChatBindingPo binding = bindingRepository
                .findByPlatformAndConnectorIdAndExternalConversationIdAndEnabledTrueAndDeletedFalse(
                        message.platform(), message.connectorId(), message.conversationId())
                .orElseThrow(() -> new ExternalChatException("EXTERNAL_CHAT_BINDING_NOT_FOUND",
                        "external conversation binding not found"));
        if (binding.getConversationType() != message.conversationType()
                || !binding.getAudienceKey().equals(message.audienceKey())) {
            throw new ExternalChatException("EXTERNAL_CHAT_BINDING_TYPE_MISMATCH",
                    "external conversation metadata differs from its binding");
        }
        AgentMemorySpacePo space = spaceRepository.findBySpaceIdAndDeletedFalse(binding.getSpaceId())
                .filter(value -> value.getStatus() == AgentMemorySpaceStatus.ACTIVE)
                .orElseThrow(() -> new ExternalChatException("AGENT_MEMORY_SPACE_DISABLED",
                        "bound memory space is unavailable"));
        return new ResolvedExternalChatBinding(space.getOwnerUserId(), space.getSpaceId(),
                binding.getPlatform(), binding.getConnectorId(), binding.getExternalConversationId(),
                binding.getConversationType(), binding.getAudienceKey());
    }
}
