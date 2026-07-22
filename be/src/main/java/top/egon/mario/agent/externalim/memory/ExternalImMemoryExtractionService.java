package top.egon.mario.agent.externalim.memory;

import top.egon.mario.agent.externalim.memory.model.ExternalImMemoryExtractionRequest;

public interface ExternalImMemoryExtractionService {

    void extractAfterReply(ExternalImMemoryExtractionRequest request);
}
