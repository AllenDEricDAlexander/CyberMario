package top.egon.mario.clocktower.agent.strategy.llm;

public interface ClocktowerAgentLlmClient {

    ClocktowerAgentLlmResponse decide(ClocktowerAgentLlmRequest request);
}
