package top.egon.mario.rbac.activation.delivery;

import top.egon.mario.rbac.activation.model.IssuedActivationToken;
import top.egon.mario.rbac.dto.response.ActivationDeliveryResponse;

/**
 * Strategy contract for activation-link delivery.
 */
public interface ActivationLinkDelivery {
    ActivationDeliveryResponse deliver(IssuedActivationToken issuedToken);
}
