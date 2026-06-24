package top.egon.mario.im.context;

import java.util.Objects;

public record ImPrincipal(Long userId) {

    public ImPrincipal {
        Objects.requireNonNull(userId, "userId must not be null");
    }
}
