package top.egon.mario.im.platform;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Settings used to seed the public platform channel.
 */
@ConfigurationProperties(prefix = "mario.im.platform.bootstrap")
public record PlatformImBootstrapProperties(
        Boolean enabled,
        String ownerAccountNo,
        String channelKey,
        String channelName
) {

    public PlatformImBootstrapProperties {
        enabled = enabled == null || enabled;
        ownerAccountNo = StringUtils.hasText(ownerAccountNo) ? ownerAccountNo.trim() : null;
        channelKey = StringUtils.hasText(channelKey) ? channelKey.trim() : "general";
        channelName = StringUtils.hasText(channelName) ? channelName.trim() : "公共频道";
    }
}
