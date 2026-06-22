package top.egon.mario.agent.soul.service;

import top.egon.mario.agent.soul.service.model.AgentSoulEvolutionDecision;
import top.egon.mario.agent.soul.service.model.AgentSoulEvolutionInput;

/**
 * Strategy for deciding whether a chat turn should rewrite the user's current SoulMD.
 */
public interface AgentSoulEvolutionModel {

    AgentSoulEvolutionDecision evaluateAndRewrite(AgentSoulEvolutionInput input);
}
