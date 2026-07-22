package top.egon.mario.agent.externalim.runtime.model;

import top.egon.mario.agent.externalim.runtime.po.enums.ExternalChatProcessingStatus;

public record ExternalChatAcceptance(
        Long eventDatabaseId,
        boolean duplicate,
        ExternalChatProcessingStatus status
) {
}
