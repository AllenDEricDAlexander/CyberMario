package top.egon.mario.im.policy;

public interface SendPolicy {

    String DEFAULT_CONTEXT_TYPE = "DEFAULT";

    String contextType();

    boolean canSend(ImAccessContext context);

    default boolean defaultPolicy() {
        return DEFAULT_CONTEXT_TYPE.equals(contextType());
    }
}
