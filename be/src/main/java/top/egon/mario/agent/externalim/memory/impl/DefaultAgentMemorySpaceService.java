package top.egon.mario.agent.externalim.memory.impl;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.agent.externalim.ExternalChatException;
import top.egon.mario.agent.externalim.memory.AgentMemorySpaceService;
import top.egon.mario.agent.externalim.memory.model.ExternalChatBindingCommand;
import top.egon.mario.agent.externalim.memory.po.AgentMemorySpacePo;
import top.egon.mario.agent.externalim.memory.po.ExternalChatBindingPo;
import top.egon.mario.agent.externalim.memory.po.enums.AgentMemorySpaceStatus;
import top.egon.mario.agent.externalim.memory.repository.AgentMemorySpaceRepository;
import top.egon.mario.agent.externalim.memory.repository.ExternalChatBindingRepository;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Instant;
import java.util.UUID;

@Service
public class DefaultAgentMemorySpaceService implements AgentMemorySpaceService {

    private final AgentMemorySpaceRepository spaceRepository;
    private final ExternalChatBindingRepository bindingRepository;

    public DefaultAgentMemorySpaceService(AgentMemorySpaceRepository spaceRepository,
                                          ExternalChatBindingRepository bindingRepository) {
        this.spaceRepository = spaceRepository;
        this.bindingRepository = bindingRepository;
    }

    @Override
    @Transactional
    public AgentMemorySpacePo create(String name, RbacPrincipal principal) {
        RbacPrincipal owner = requirePrincipal(principal);
        if (!StringUtils.hasText(name)) {
            throw new ExternalChatException("AGENT_MEMORY_SPACE_NAME_REQUIRED",
                    "memory space name is required");
        }
        Instant now = Instant.now();
        AgentMemorySpacePo space = new AgentMemorySpacePo();
        space.setSpaceId(UUID.randomUUID().toString());
        space.setOwnerUserId(owner.userId());
        space.setName(name.trim());
        space.setStatus(AgentMemorySpaceStatus.ACTIVE);
        space.setCreatedAt(now);
        space.setUpdatedAt(now);
        return spaceRepository.save(space);
    }

    @Override
    @Transactional(readOnly = true)
    public AgentMemorySpacePo requireOwned(String memorySpaceId, RbacPrincipal principal) {
        RbacPrincipal owner = requirePrincipal(principal);
        if (!StringUtils.hasText(memorySpaceId)) {
            throw new ExternalChatException("AGENT_MEMORY_SPACE_NOT_FOUND",
                    "memory space not found");
        }
        AgentMemorySpacePo space = spaceRepository
                .findBySpaceIdAndOwnerUserIdAndDeletedFalse(memorySpaceId.trim(), owner.userId())
                .orElseThrow(() -> new ExternalChatException("AGENT_MEMORY_SPACE_NOT_FOUND",
                        "memory space not found"));
        if (space.getStatus() != AgentMemorySpaceStatus.ACTIVE) {
            throw new ExternalChatException("AGENT_MEMORY_SPACE_DISABLED",
                    "memory space is disabled");
        }
        return space;
    }

    @Override
    @Transactional
    public ExternalChatBindingPo bind(ExternalChatBindingCommand command, RbacPrincipal principal) {
        if (command == null || command.platform() == null || command.conversationType() == null
                || !StringUtils.hasText(command.connectorId())
                || !StringUtils.hasText(command.conversationId())
                || !StringUtils.hasText(command.audienceKey())) {
            throw new ExternalChatException("EXTERNAL_CHAT_BINDING_INVALID",
                    "external chat binding is invalid");
        }
        AgentMemorySpacePo space = requireOwned(command.memorySpaceId(), principal);
        Instant now = Instant.now();
        ExternalChatBindingPo binding = new ExternalChatBindingPo();
        binding.setSpaceId(space.getSpaceId());
        binding.setPlatform(command.platform());
        binding.setConnectorId(command.connectorId().trim());
        binding.setExternalConversationId(command.conversationId().trim());
        binding.setConversationType(command.conversationType());
        binding.setAudienceKey(command.audienceKey().trim());
        binding.setEnabled(true);
        binding.setCreatedAt(now);
        binding.setUpdatedAt(now);
        try {
            return bindingRepository.saveAndFlush(binding);
        } catch (DataIntegrityViolationException error) {
            throw new ExternalChatException("EXTERNAL_CHAT_BINDING_EXISTS",
                    "external conversation is already bound");
        }
    }

    private RbacPrincipal requirePrincipal(RbacPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new ExternalChatException("EXTERNAL_CHAT_OWNER_REQUIRED",
                    "authenticated memory space owner is required");
        }
        return principal;
    }
}
