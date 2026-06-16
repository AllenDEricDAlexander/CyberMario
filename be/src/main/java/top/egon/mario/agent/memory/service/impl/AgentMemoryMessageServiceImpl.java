package top.egon.mario.agent.memory.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.agent.memory.po.AgentMemoryMessagePo;
import top.egon.mario.agent.memory.po.AgentMemorySessionPo;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageRole;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageType;
import top.egon.mario.agent.memory.repository.AgentMemoryMessageRepository;
import top.egon.mario.agent.memory.repository.AgentMemorySessionRepository;
import top.egon.mario.agent.memory.service.AgentMemoryException;
import top.egon.mario.agent.memory.service.AgentMemoryMessageService;
import top.egon.mario.agent.memory.service.model.AgentMemoryMessageRecord;
import top.egon.mario.agent.memory.service.model.AgentMemoryTurn;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of persisted memory message sequencing.
 */
@Service
public class AgentMemoryMessageServiceImpl implements AgentMemoryMessageService {

    private final AgentMemoryMessageRepository messageRepository;
    private final AgentMemorySessionRepository sessionRepository;

    public AgentMemoryMessageServiceImpl(AgentMemoryMessageRepository messageRepository,
                                         AgentMemorySessionRepository sessionRepository) {
        this.messageRepository = messageRepository;
        this.sessionRepository = sessionRepository;
    }

    @Override
    @Transactional
    public List<AgentMemoryMessagePo> appendAll(List<AgentMemoryMessageRecord> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        String sessionId = records.getFirst().sessionId();
        int seqNo = currentMaxSeq(sessionId) + 1;
        Instant now = Instant.now();
        List<AgentMemoryMessagePo> messages = new ArrayList<>(records.size());
        for (AgentMemoryMessageRecord record : records) {
            AgentMemoryMessagePo message = new AgentMemoryMessagePo();
            message.setSessionId(record.sessionId());
            message.setUserId(record.userId());
            message.setEntryType(record.entryType());
            message.setSeqNo(seqNo++);
            message.setTurnNo(record.turnNo());
            message.setRole(record.role());
            message.setMessageType(record.messageType());
            message.setContent(record.content());
            message.setContentChars(record.content() == null ? 0 : record.content().length());
            message.setSourceRefsJson(record.sourceRefsJson());
            message.setTraceId(record.traceId());
            message.setRequestId(record.requestId());
            message.setCreatedAt(now);
            messages.add(message);
        }
        return messageRepository.saveAll(messages);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentMemoryMessagePo> messages(String sessionId, RbacPrincipal principal) {
        requirePrincipal(principal);
        sessionRepository.findBySessionIdAndUserIdAndDeletedFalse(sessionId, principal.userId())
                .orElseThrow(() -> new AgentMemoryException("AGENT_MEMORY_SESSION_NOT_FOUND",
                        "memory session not found"));
        return messageRepository.findBySessionIdAndDeletedFalseOrderBySeqNoAsc(sessionId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentMemoryTurn> recentTurns(AgentMemorySessionPo session) {
        if (session == null || session.getShortTermWindowTurns() <= 0) {
            return List.of();
        }
        List<AgentMemoryMessagePo> rows =
                messageRepository.findTop40BySessionIdAndDeletedFalseOrderBySeqNoDesc(session.getSessionId())
                        .stream()
                        .sorted(Comparator.comparingInt(AgentMemoryMessagePo::getSeqNo))
                        .toList();
        Map<Integer, TurnBuilder> turns = new LinkedHashMap<>();
        for (AgentMemoryMessagePo row : rows) {
            if (row.getMessageType() != AgentMemoryMessageType.MESSAGE) {
                continue;
            }
            TurnBuilder builder = turns.computeIfAbsent(row.getTurnNo(), ignored -> new TurnBuilder());
            if (row.getRole() == AgentMemoryMessageRole.USER) {
                builder.userMessage = row.getContent();
            } else if (row.getRole() == AgentMemoryMessageRole.ASSISTANT) {
                builder.assistantMessage = row.getContent();
            }
        }
        List<AgentMemoryTurn> completeTurns = turns.values().stream()
                .filter(TurnBuilder::complete)
                .map(builder -> new AgentMemoryTurn(builder.userMessage, builder.assistantMessage))
                .toList();
        int fromIndex = Math.max(0, completeTurns.size() - session.getShortTermWindowTurns());
        return completeTurns.subList(fromIndex, completeTurns.size());
    }

    @Override
    @Transactional(readOnly = true)
    public int nextTurnNo(String sessionId) {
        return messageRepository.findBySessionIdAndDeletedFalseOrderBySeqNoAsc(sessionId).stream()
                .mapToInt(AgentMemoryMessagePo::getTurnNo)
                .max()
                .orElse(0) + 1;
    }

    private int currentMaxSeq(String sessionId) {
        return messageRepository.findBySessionIdAndDeletedFalseOrderBySeqNoAsc(sessionId).stream()
                .mapToInt(AgentMemoryMessagePo::getSeqNo)
                .max()
                .orElse(0);
    }

    private void requirePrincipal(RbacPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new AgentMemoryException("AGENT_MEMORY_UNAUTHENTICATED", "memory requires an authenticated user");
        }
    }

    private static final class TurnBuilder {

        private String userMessage;

        private String assistantMessage;

        private boolean complete() {
            return userMessage != null && assistantMessage != null;
        }
    }
}
