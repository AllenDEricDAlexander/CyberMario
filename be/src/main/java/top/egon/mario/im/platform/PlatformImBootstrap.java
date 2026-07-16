package top.egon.mario.im.platform;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import top.egon.mario.common.utils.LogUtil;
import top.egon.mario.im.facade.dto.view.ChannelView;
import top.egon.mario.im.policy.ImPrincipal;
import top.egon.mario.rbac.application.RbacUserDirectoryFacade;
import top.egon.mario.rbac.dto.response.UserDirectoryItemResponse;
import top.egon.mario.rbac.service.bootstrap.RbacAdminBootstrapProperties;

import java.util.Map;
import java.util.Set;

/**
 * Idempotently creates the public platform channel after the RBAC administrator bootstrap.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 5)
@Slf4j
public class PlatformImBootstrap implements ApplicationRunner {

    private final PlatformImBootstrapProperties properties;
    private final PlatformRoomFacade platformRoomFacade;
    private final RbacUserDirectoryFacade userDirectoryFacade;
    private final RbacAdminBootstrapProperties adminBootstrapProperties;

    public PlatformImBootstrap(PlatformImBootstrapProperties properties,
                               PlatformRoomFacade platformRoomFacade,
                               RbacUserDirectoryFacade userDirectoryFacade,
                               RbacAdminBootstrapProperties adminBootstrapProperties) {
        this.properties = properties;
        this.platformRoomFacade = platformRoomFacade;
        this.userDirectoryFacade = userDirectoryFacade;
        this.adminBootstrapProperties = adminBootstrapProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        bootstrap();
    }

    public ChannelView bootstrap() {
        if (!properties.enabled()) {
            LogUtil.debug(log).log("platform IM bootstrap skipped, enabled=false");
            return null;
        }
        String ownerAccountNo = properties.ownerAccountNo() == null
                ? adminBootstrapProperties.username()
                : properties.ownerAccountNo();
        UserDirectoryItemResponse owner = userDirectoryFacade.findEnabledByAccountNo(ownerAccountNo)
                .orElseThrow(() -> new IllegalStateException(
                        "Platform IM bootstrap owner does not exist or is unavailable: "
                                + ownerAccountNo));
        ImPrincipal principal = new ImPrincipal(
                owner.userId(), Set.of(), PlatformRoomFacade.PLATFORM_CONTEXT_TYPE, Map.of());
        ChannelView channel = platformRoomFacade.createGeneralChannel(
                principal, properties.channelKey(), properties.channelName());
        LogUtil.info(log).log("platform IM bootstrap completed, channelKey={}, channelId={}, ownerAccountNo={}",
                channel.channelKey(), channel.id(), ownerAccountNo);
        return channel;
    }
}
