package top.egon.mario.im.platform;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enables platform Web IM configuration properties.
 */
@Configuration
@EnableConfigurationProperties(ImUserRoleBackfillProperties.class)
public class PlatformImConfiguration {
}
