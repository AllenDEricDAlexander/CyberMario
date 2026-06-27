package top.egon.mario.im.policy;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PolicyRegistry {

    private final Map<String, SendPolicy> sendPoliciesByContextType;
    private final Map<String, VisibilityPolicy> visibilityPoliciesByContextType;
    private final DefaultSendPolicy defaultSendPolicy;
    private final DefaultVisibilityPolicy defaultVisibilityPolicy;

    public PolicyRegistry(List<SendPolicy> sendPolicies, List<VisibilityPolicy> visibilityPolicies) {
        this.defaultSendPolicy = sendPolicies.stream()
                .filter(DefaultSendPolicy.class::isInstance)
                .map(DefaultSendPolicy.class::cast)
                .findFirst()
                .orElseGet(DefaultSendPolicy::new);
        this.defaultVisibilityPolicy = visibilityPolicies.stream()
                .filter(DefaultVisibilityPolicy.class::isInstance)
                .map(DefaultVisibilityPolicy.class::cast)
                .findFirst()
                .orElseGet(DefaultVisibilityPolicy::new);
        this.sendPoliciesByContextType = registerSendPolicies(sendPolicies);
        this.visibilityPoliciesByContextType = registerVisibilityPolicies(visibilityPolicies);
    }

    public SendPolicy resolveSendPolicy(String contextType) {
        SendPolicy policy = sendPoliciesByContextType.get(contextType);
        return policy == null ? defaultSendPolicy : policy;
    }

    public VisibilityPolicy resolveVisibilityPolicy(String contextType) {
        VisibilityPolicy policy = visibilityPoliciesByContextType.get(contextType);
        return policy == null ? defaultVisibilityPolicy : policy;
    }

    public <T> T resolve(String contextType, Class<T> policyClass) {
        if (SendPolicy.class.equals(policyClass)) {
            return policyClass.cast(resolveSendPolicy(contextType));
        }
        if (VisibilityPolicy.class.equals(policyClass)) {
            return policyClass.cast(resolveVisibilityPolicy(contextType));
        }
        throw new IllegalArgumentException("Unsupported IM policy class: " + policyClass.getName());
    }

    private Map<String, SendPolicy> registerSendPolicies(List<SendPolicy> policies) {
        Map<String, SendPolicy> byContextType = new HashMap<>();
        for (SendPolicy policy : policies) {
            if (policy.defaultPolicy()) {
                continue;
            }
            String contextType = requireContextType(policy.contextType(), policy.getClass());
            SendPolicy previous = byContextType.putIfAbsent(contextType, policy);
            if (previous != null) {
                throw duplicatePolicy(contextType, previous.getClass(), policy.getClass());
            }
        }
        return Map.copyOf(byContextType);
    }

    private Map<String, VisibilityPolicy> registerVisibilityPolicies(List<VisibilityPolicy> policies) {
        Map<String, VisibilityPolicy> byContextType = new HashMap<>();
        for (VisibilityPolicy policy : policies) {
            if (policy.defaultPolicy()) {
                continue;
            }
            String contextType = requireContextType(policy.contextType(), policy.getClass());
            VisibilityPolicy previous = byContextType.putIfAbsent(contextType, policy);
            if (previous != null) {
                throw duplicatePolicy(contextType, previous.getClass(), policy.getClass());
            }
        }
        return Map.copyOf(byContextType);
    }

    private String requireContextType(String contextType, Class<?> policyClass) {
        if (contextType == null || contextType.isBlank()) {
            throw new IllegalStateException("IM policy context type is required: " + policyClass.getName());
        }
        return contextType;
    }

    private IllegalStateException duplicatePolicy(String contextType, Class<?> firstClass, Class<?> secondClass) {
        return new IllegalStateException("Duplicate IM policy for context type " + contextType
                + ": " + firstClass.getName() + ", " + secondClass.getName());
    }
}
