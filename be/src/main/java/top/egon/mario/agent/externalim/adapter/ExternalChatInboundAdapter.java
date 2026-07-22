package top.egon.mario.agent.externalim.adapter;

import top.egon.mario.agent.externalim.model.ExternalChatMessage;
import top.egon.mario.agent.externalim.model.ExternalChatPlatform;

public interface ExternalChatInboundAdapter {

    ExternalChatPlatform platform();

    ExternalChatMessage verifyAndNormalize(ExternalWebhookRequest request);
}
