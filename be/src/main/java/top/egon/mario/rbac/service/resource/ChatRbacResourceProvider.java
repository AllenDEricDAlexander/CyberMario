package top.egon.mario.rbac.service.resource;

import org.springframework.stereotype.Component;
import top.egon.mario.rbac.service.resource.model.RbacResourceSeed;
import top.egon.mario.rbac.service.resource.model.RbacResourceSource;
import top.egon.mario.rbac.service.resource.model.RbacRolePresetSeed;

import java.util.List;

/**
 * Supplies Chat role presets to the unified RBAC resource synchronizer.
 */
@Component
public class ChatRbacResourceProvider implements RbacResourceProvider {

    private static final String APP_CODE = "chat";

    @Override
    public String appCode() {
        return APP_CODE;
    }

    @Override
    public List<RbacResourceSeed> resources() {
        return List.of();
    }

    @Override
    public List<RbacRolePresetSeed> rolePresets() {
        return List.of(new RbacRolePresetSeed(
                APP_CODE,
                "CHAT_BASIC",
                "Chat Basic User",
                "System role for the basic agent chat console.",
                50,
                List.of("menu:chat", "api:chat:stream", "api:rbac:auth:self", "api:rbac:me:self",
                        "menu:agent",
                        "api:agent:model-audit:dashboard:self",
                        "menu:agent:debug",
                        "api:agent:debug:chat:stream",
                        "api:agent:preset:collection",
                        "api:agent:preset:*"),
                RbacResourceSource.PROVIDER
        ));
    }

}
