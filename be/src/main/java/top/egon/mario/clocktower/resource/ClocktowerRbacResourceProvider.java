package top.egon.mario.clocktower.resource;

import org.springframework.stereotype.Component;
import top.egon.mario.rbac.po.enums.ApiMatcherType;
import top.egon.mario.rbac.po.enums.ApiRiskLevel;
import top.egon.mario.rbac.po.enums.PermissionStatus;
import top.egon.mario.rbac.service.resource.RbacResourceProvider;
import top.egon.mario.rbac.service.resource.model.RbacApiSeed;
import top.egon.mario.rbac.service.resource.model.RbacMenuSeed;
import top.egon.mario.rbac.service.resource.model.RbacResourceSeed;
import top.egon.mario.rbac.service.resource.model.RbacResourceSource;
import top.egon.mario.rbac.service.resource.model.RbacRolePresetSeed;

import java.util.ArrayList;
import java.util.List;

@Component
public class ClocktowerRbacResourceProvider implements RbacResourceProvider {

    private static final String APP_CODE = "clocktower";

    @Override
    public String appCode() {
        return APP_CODE;
    }

    @Override
    public List<RbacResourceSeed> resources() {
        List<RbacResourceSeed> resources = new ArrayList<>();
        resources.add(menu("menu:clocktower:boards", "钟楼配板", null, "/clocktower/boards", 10));
        resources.add(menu("menu:clocktower:rooms", "钟楼房间", null, "/clocktower/rooms", 20));
        resources.add(menu("menu:clocktower:rules", "钟楼规则", null, "/clocktower/rules", 30));
        resources.add(menu("menu:clocktower:replays", "钟楼回放", null, "/clocktower/replays", 40));

        resources.add(api("api:clocktower:scripts:*", "Clocktower scripts", "GET", "/api/clocktower/scripts/**", ApiRiskLevel.LOW));
        resources.add(api("api:clocktower:boards:*", "Clocktower boards", "ANY", "/api/clocktower/boards/**", ApiRiskLevel.MEDIUM));
        resources.add(api("api:clocktower:rooms:read:list", "Clocktower room list", "GET",
                "/api/clocktower/rooms", ApiRiskLevel.LOW));
        resources.add(api("api:clocktower:rooms:read:detail", "Clocktower room detail", "GET",
                "/api/clocktower/rooms/*", ApiRiskLevel.LOW));
        resources.add(api("api:clocktower:rooms:player:join", "Clocktower player join", "POST",
                "/api/clocktower/rooms/*/join", ApiRiskLevel.MEDIUM));
        resources.add(api("api:clocktower:rooms:player:leave", "Clocktower player leave", "POST",
                "/api/clocktower/rooms/*/leave", ApiRiskLevel.MEDIUM));
        resources.add(api("api:clocktower:rooms:player:view", "Clocktower player view", "GET",
                "/api/clocktower/rooms/*/view", ApiRiskLevel.MEDIUM));
        resources.add(api("api:clocktower:rooms:player:action", "Clocktower player action", "POST",
                "/api/clocktower/rooms/*/actions", ApiRiskLevel.MEDIUM));
        resources.add(api("api:clocktower:rooms:storyteller:create", "Clocktower create room", "POST",
                "/api/clocktower/rooms", ApiRiskLevel.MEDIUM));
        resources.add(api("api:clocktower:rooms:storyteller:start", "Clocktower start room", "POST",
                "/api/clocktower/rooms/*/start", ApiRiskLevel.HIGH));
        resources.add(api("api:clocktower:rooms:storyteller:seat", "Clocktower update seat", "PATCH",
                "/api/clocktower/rooms/*/seats/*", ApiRiskLevel.HIGH));
        resources.add(api("api:clocktower:rooms:storyteller:night", "Clocktower night checklist", "GET",
                "/api/clocktower/rooms/*/night-checklist", ApiRiskLevel.HIGH));
        resources.add(api("api:clocktower:rooms:storyteller:action", "Clocktower storyteller action", "POST",
                "/api/clocktower/rooms/*/storyteller/actions", ApiRiskLevel.HIGH));
        resources.add(api("api:clocktower:events:stream", "Clocktower event stream", "GET",
                "/api/clocktower/rooms/*/events/stream", ApiRiskLevel.MEDIUM));
        resources.add(api("api:clocktower:grimoire:*", "Clocktower grimoire", "ANY",
                "/api/clocktower/rooms/*/grimoire/**", ApiRiskLevel.HIGH));
        resources.add(api("api:clocktower:replays:*", "Clocktower replays", "ANY", "/api/clocktower/replays/**", ApiRiskLevel.MEDIUM));
        return resources;
    }

    @Override
    public List<RbacRolePresetSeed> rolePresets() {
        return List.of(
                new RbacRolePresetSeed(APP_CODE, "CLOCKTOWER_PLAYER", "Clocktower Player",
                        "System role for Clocktower players.", 40,
                        List.of(
                                "menu:clocktower:rooms", "menu:clocktower:replays",
                                "api:clocktower:scripts:*", "api:clocktower:rooms:read:list",
                                "api:clocktower:rooms:read:detail", "api:clocktower:rooms:player:join",
                                "api:clocktower:rooms:player:leave", "api:clocktower:rooms:player:view",
                                "api:clocktower:rooms:player:action",
                                "api:clocktower:events:stream", "api:clocktower:replays:*"
                        ), RbacResourceSource.PROVIDER),
                new RbacRolePresetSeed(APP_CODE, "CLOCKTOWER_STORYTELLER", "Clocktower Storyteller",
                        "System role for Clocktower storytellers.", 30,
                        List.of(
                                "menu:clocktower:boards", "menu:clocktower:rooms", "menu:clocktower:rules",
                                "menu:clocktower:replays", "api:clocktower:scripts:*", "api:clocktower:boards:*",
                                "api:clocktower:rooms:read:list", "api:clocktower:rooms:read:detail",
                                "api:clocktower:rooms:player:join", "api:clocktower:rooms:player:leave",
                                "api:clocktower:rooms:player:view", "api:clocktower:rooms:player:action",
                                "api:clocktower:rooms:storyteller:create",
                                "api:clocktower:rooms:storyteller:start",
                                "api:clocktower:rooms:storyteller:seat",
                                "api:clocktower:rooms:storyteller:night",
                                "api:clocktower:rooms:storyteller:action", "api:clocktower:events:stream",
                                "api:clocktower:grimoire:*", "api:clocktower:replays:*"
                        ), RbacResourceSource.PROVIDER)
        );
    }

    private RbacResourceSeed menu(String code, String name, String parentCode, String path, int sortNo) {
        return RbacResourceSeed.menu(APP_CODE, APP_CODE, code, name, parentCode, PermissionStatus.ENABLED,
                sortNo, null, new RbacMenuSeed(code.replace("menu:", ""), path, null, null, "CrownOutlined",
                        false, true, null), RbacResourceSource.PROVIDER);
    }

    private RbacResourceSeed api(String code, String name, String method, String pattern, ApiRiskLevel riskLevel) {
        return RbacResourceSeed.api(APP_CODE, APP_CODE, code, name, PermissionStatus.ENABLED, 0, null,
                new RbacApiSeed(method, pattern, ApiMatcherType.ANT, false, riskLevel), RbacResourceSource.PROVIDER);
    }
}
