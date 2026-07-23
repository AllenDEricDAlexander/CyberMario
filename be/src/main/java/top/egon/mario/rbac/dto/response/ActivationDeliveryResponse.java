package top.egon.mario.rbac.dto.response;

import top.egon.mario.rbac.activation.delivery.ActivationDeliveryMode;

import java.time.Instant;

/**
 * Admin-only activation delivery result.
 */
public record ActivationDeliveryResponse(
        ActivationDeliveryMode mode,
        Instant expiresAt,
        String mockActivationUrl
) {
}
