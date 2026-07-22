package top.egon.mario.agent.externalim;

public class ExternalChatException extends RuntimeException {

    private final String code;

    public ExternalChatException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
