package top.egon.mario.agent.externalim.adapter;

import top.egon.mario.agent.externalim.model.ExternalChatMessage;
import top.egon.mario.agent.externalim.model.ExternalChatPlatform;

import java.util.Optional;

public interface ExternalChatInboundAdapter {

    ExternalChatPlatform platform();

    Optional<ExternalChatMessage> verifyAndNormalize(ExternalWebhookRequest request);
}
