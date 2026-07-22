package top.egon.mario.agent.externalim.runtime;

import top.egon.mario.agent.externalim.model.ExternalChatMessage;
import top.egon.mario.agent.externalim.runtime.model.ExternalChatAcceptance;

public interface ExternalChatIngressService {

    ExternalChatAcceptance accept(ExternalChatMessage message, String traceId);
}
