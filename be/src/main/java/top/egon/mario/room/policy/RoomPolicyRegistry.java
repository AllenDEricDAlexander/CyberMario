package top.egon.mario.room.policy;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class RoomPolicyRegistry {

    private final Map<Class<?>, Map<String, Object>> policiesByClass = new HashMap<>();
    private final RoomMutationPolicy defaultMutationPolicy = new DefaultDenyMutationPolicy();
    private final RoomReadPolicy defaultReadPolicy = new DefaultPublicReadPolicy();

    public RoomPolicyRegistry(List<RoomMutationPolicy> mutationPolicies, List<RoomReadPolicy> readPolicies) {
        register(RoomMutationPolicy.class, mutationPolicies);
        register(RoomReadPolicy.class, readPolicies);
    }

    public <T> T resolve(String contextType, Class<T> policyClass) {
        Object policy = policiesByClass.getOrDefault(policyClass, Map.of()).get(contextType);
        if (policyClass.isInstance(policy)) {
            return policyClass.cast(policy);
        }
        if (policyClass == RoomMutationPolicy.class) {
            return policyClass.cast(defaultMutationPolicy);
        }
        if (policyClass == RoomReadPolicy.class) {
            return policyClass.cast(defaultReadPolicy);
        }
        throw new IllegalArgumentException("Unsupported room policy class: " + policyClass.getName());
    }

    private <T extends RoomTypedPolicy> void register(Class<T> policyClass, List<T> policies) {
        Map<String, Object> byContextType = new HashMap<>();
        for (T policy : policies) {
            byContextType.put(policy.contextType(), policy);
        }
        policiesByClass.put(policyClass, Map.copyOf(byContextType));
    }

    private static final class DefaultDenyMutationPolicy implements RoomMutationPolicy {

        @Override
        public String contextType() {
            return "DEFAULT";
        }

        @Override
        public boolean canMutate(top.egon.mario.room.context.RoomContext context, RoomMutation mutation) {
            return false;
        }
    }

    private static final class DefaultPublicReadPolicy implements RoomReadPolicy {

        @Override
        public String contextType() {
            return "DEFAULT";
        }

        @Override
        public boolean canList(top.egon.mario.room.context.RoomContext context) {
            return context != null && "PUBLIC".equals(context.roomVisibility());
        }
    }
}
