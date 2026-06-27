package top.egon.mario.im.policy;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record ImPrincipal(
        Long userId,
        Set<String> roleCodes,
        String contextType,
        Map<String, String> attributes) {

    public ImPrincipal {
        Objects.requireNonNull(userId, "userId must not be null");
        roleCodes = roleCodes == null ? Set.of() : Set.copyOf(roleCodes);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
