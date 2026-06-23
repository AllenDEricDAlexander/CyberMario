package top.egon.mario.rbac.service.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Browser cookie settings for RBAC token transport.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "mario.security.browser-cookie")
public class BrowserAuthCookieProperties {

    private boolean enabled = true;
    private boolean secure = true;
    private String sameSite = "Lax";
    private String accessCookieName = "CM_ACCESS_TOKEN";
    private String refreshCookieName = "CM_REFRESH_TOKEN";
    private String accessCookiePath = "/api";
    private String refreshCookiePath = "/api/auth";
    private String browserHeaderName = "X-Client-Type";
    private String browserHeaderValue = "browser";

}
