package top.egon.mario.agent.memory.service;

import top.egon.mario.agent.memory.service.model.AgentMemoryExtractionRequest;

public interface AgentMemoryExtractionService {

    void extractAfterTurn(AgentMemoryExtractionRequest request);
}
