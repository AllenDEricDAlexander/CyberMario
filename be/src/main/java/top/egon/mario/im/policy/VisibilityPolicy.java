package top.egon.mario.im.policy;

public interface VisibilityPolicy {

    String DEFAULT_CONTEXT_TYPE = "DEFAULT";

    String contextType();

    boolean canRead(ImAccessContext context);

    default boolean defaultPolicy() {
        return DEFAULT_CONTEXT_TYPE.equals(contextType());
    }
}
