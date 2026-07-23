package top.egon.mario.rbac.dto.response;

/**
 * Administrator response pairing a pending user with its delivery metadata.
 */
public record AdminUserCreateResponse(
        UserResponse user,
        ActivationDeliveryResponse activationDelivery
) {
}
