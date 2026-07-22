package top.egon.mario.agent.externalim.memory;

import top.egon.mario.agent.externalim.memory.model.ResolvedExternalChatBinding;
import top.egon.mario.agent.externalim.model.ExternalChatMessage;

public interface ExternalChatBindingResolver {

    ResolvedExternalChatBinding resolve(ExternalChatMessage message);
}
