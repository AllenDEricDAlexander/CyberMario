package top.egon.mario.rbac.activation.model;

import org.springframework.security.authentication.ott.OneTimeToken;
import top.egon.mario.rbac.po.OneTimeTokenPo;

/**
 * Package model carrying a locked token row and reconstructed Spring token.
 */
public record StoredActivationToken(OneTimeTokenPo row, OneTimeToken token) {
}
