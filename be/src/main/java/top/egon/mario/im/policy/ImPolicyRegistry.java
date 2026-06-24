package top.egon.mario.im.policy;

import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;
import top.egon.mario.im.context.ImContext;

import java.util.ArrayList;
import java.util.List;

@Component
public class ImPolicyRegistry {

    private final List<ImSendPolicy> sendPolicies;
    private final List<ImVisibilityPolicy> visibilityPolicies;
    private final ImSendPolicy fallbackSendPolicy = new DenySendPolicy();
    private final ImVisibilityPolicy fallbackVisibilityPolicy = new MemberVisibilityPolicy();

    public ImPolicyRegistry(List<ImSendPolicy> sendPolicies, List<ImVisibilityPolicy> visibilityPolicies) {
        this.sendPolicies = new ArrayList<>(sendPolicies);
        this.visibilityPolicies = new ArrayList<>(visibilityPolicies);
        AnnotationAwareOrderComparator.sort(this.sendPolicies);
        AnnotationAwareOrderComparator.sort(this.visibilityPolicies);
    }

    public ImSendPolicy resolveSendPolicy(String contextType) {
        return sendPolicies.stream()
                .filter(policy -> policy.supports(contextType))
                .findFirst()
                .orElse(fallbackSendPolicy);
    }

    public ImVisibilityPolicy resolveVisibilityPolicy(String contextType) {
        return visibilityPolicies.stream()
                .filter(policy -> policy.supports(contextType))
                .findFirst()
                .orElse(fallbackVisibilityPolicy);
    }

    @SuppressWarnings("unchecked")
    public <T> T resolve(String contextType, Class<T> policyClass) {
        if (ImSendPolicy.class.equals(policyClass)) {
            return (T) resolveSendPolicy(contextType);
        }
        if (ImVisibilityPolicy.class.equals(policyClass)) {
            return (T) resolveVisibilityPolicy(contextType);
        }
        throw new IllegalArgumentException("Unsupported IM policy type: " + policyClass.getName());
    }

    private static final class DenySendPolicy implements ImSendPolicy {

        @Override
        public boolean supports(String contextType) {
            return true;
        }

        @Override
        public boolean canSend(ImContext context) {
            return false;
        }
    }

    private static final class MemberVisibilityPolicy implements ImVisibilityPolicy {

        @Override
        public boolean supports(String contextType) {
            return true;
        }

        @Override
        public boolean canRead(ImContext context) {
            return context.activeConversationMember();
        }
    }
}
