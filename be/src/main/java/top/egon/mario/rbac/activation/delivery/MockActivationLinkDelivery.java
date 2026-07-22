package top.egon.mario.rbac.activation.delivery;

import lombok.RequiredArgsConstructor;
import top.egon.mario.rbac.activation.config.RbacOttProperties;
import top.egon.mario.rbac.activation.model.IssuedActivationToken;
import top.egon.mario.rbac.dto.response.ActivationDeliveryResponse;

/**
 * Trusted-base URL builder that returns, but never logs, the raw activation link.
 */
@RequiredArgsConstructor
public class MockActivationLinkDelivery implements ActivationLinkDelivery {

    private final RbacOttProperties properties;

    @Override
    public ActivationDeliveryResponse deliver(IssuedActivationToken issuedToken) {
        String baseUrl = properties.publicBaseUrl().toString().replaceFirst("/+$", "");
        String activationUrl = baseUrl + "/activate#token=" + issuedToken.token().getTokenValue();
        return new ActivationDeliveryResponse(ActivationDeliveryMode.MOCK,
                issuedToken.token().getExpiresAt(), activationUrl);
    }
}
