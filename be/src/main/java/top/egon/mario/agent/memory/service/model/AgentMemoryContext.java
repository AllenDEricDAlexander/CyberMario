package top.egon.mario.agent.memory.service.model;

public record AgentMemoryContext(String shortTermPrompt, String longTermPrompt) {

    public boolean isEmpty() {
        return (shortTermPrompt == null || shortTermPrompt.isBlank())
                && (longTermPrompt == null || longTermPrompt.isBlank());
    }
}
