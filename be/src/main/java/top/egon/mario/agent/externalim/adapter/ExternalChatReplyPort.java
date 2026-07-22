package top.egon.mario.agent.externalim.adapter;

import top.egon.mario.agent.externalim.model.ExternalChatPlatform;
import top.egon.mario.agent.externalim.model.ExternalReplyCommand;
import top.egon.mario.agent.externalim.model.ExternalReplyResult;

public interface ExternalChatReplyPort {

    ExternalChatPlatform platform();

    ExternalReplyResult send(ExternalReplyCommand command);
}
