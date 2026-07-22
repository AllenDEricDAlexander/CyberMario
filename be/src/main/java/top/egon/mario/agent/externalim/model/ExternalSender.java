package top.egon.mario.agent.externalim.model;

public record ExternalSender(String id, String displayName, ExternalSenderType type) {

    public ExternalSender {
        type = type == null ? ExternalSenderType.SYSTEM : type;
    }
}
