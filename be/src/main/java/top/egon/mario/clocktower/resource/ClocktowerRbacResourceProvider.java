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
    private static final String ADMIN_APP_CODE = "admin";

    private static final String SCRIPT_READ = "api:clocktower:script:read";
    private static final String BOARD_ALL = "api:clocktower:board:*";
    private static final String ROOM_READ = "api:clocktower:room:read";
    private static final String ROOM_CREATE = "api:clocktower:room:create";
    private static final String ROOM_MEMBERSHIP = "api:clocktower:room:membership";
    private static final String ROOM_SEAT = "api:clocktower:room:seat";
    private static final String ROOM_GOVERNANCE = "api:clocktower:room:governance";
    private static final String GAME_READ = "api:clocktower:game:read";
    private static final String GAME_LIFECYCLE = "api:clocktower:game:lifecycle";
    private static final String GAME_ACTION = "api:clocktower:game:action";
    private static final String GAME_STORYTELLER = "api:clocktower:game:storyteller";
    private static final String GAME_MIC_READ = "api:clocktower:game:mic:read";
    private static final String GAME_MIC_PLAYER = "api:clocktower:game:mic:player";
    private static final String GAME_MIC_STORYTELLER = "api:clocktower:game:mic:storyteller";
    private static final String GAME_EVENT_STREAM = "api:clocktower:game:event-stream";
    private static final String GAME_REPLAY = "api:clocktower:game:replay";
    private static final String CHAT_READ = "api:clocktower:chat:read";
    private static final String CHAT_SEND = "api:clocktower:chat:send";
    private static final String CHAT_CONVERSATION = "api:clocktower:chat:conversation";
    private static final String CHAT_READ_STATE = "api:clocktower:chat:read-state";
    private static final String ADMIN_AUDIT_MENU = "menu:admin:clocktower:audit";
    private static final String ADMIN_AUDIT = "api:admin:clocktower:audit";
    private static final String ADMIN_RULE_DATA = "api:admin:clocktower:rule-data";

    private static final List<String> PLAYER_PERMISSION_CODES = List.of(
            "menu:clocktower:rooms", "menu:clocktower:replays",
            SCRIPT_READ, ROOM_READ, ROOM_MEMBERSHIP, ROOM_SEAT,
            GAME_READ, GAME_ACTION, GAME_MIC_READ, GAME_MIC_PLAYER, GAME_EVENT_STREAM, GAME_REPLAY,
            CHAT_READ, CHAT_SEND, CHAT_CONVERSATION, CHAT_READ_STATE
    );

    private static final List<String> STORYTELLER_PERMISSION_CODES = List.of(
            "menu:clocktower:boards", "menu:clocktower:rooms", "menu:clocktower:rules", "menu:clocktower:replays",
            SCRIPT_READ, BOARD_ALL, ROOM_READ, ROOM_MEMBERSHIP, ROOM_SEAT, ROOM_CREATE, ROOM_GOVERNANCE,
            GAME_READ, GAME_ACTION, GAME_MIC_READ, GAME_MIC_PLAYER, GAME_MIC_STORYTELLER,
            GAME_EVENT_STREAM, GAME_REPLAY, GAME_LIFECYCLE, GAME_STORYTELLER,
            CHAT_READ, CHAT_SEND, CHAT_CONVERSATION, CHAT_READ_STATE
    );

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

        resources.add(api(SCRIPT_READ, "Clocktower script data", "GET",
                "^/api/clocktower/(scripts(/.*)?|terms|jinx-rules)$", ApiMatcherType.REGEX, ApiRiskLevel.LOW));
        resources.add(api(BOARD_ALL, "Clocktower boards", "ANY",
                "^/api/clocktower/boards(/.*)?$", ApiMatcherType.REGEX, ApiRiskLevel.MEDIUM));
        resources.add(api(ROOM_READ, "Clocktower room read", "GET",
                "^/api/clocktower/rooms(/[^/]+)?$", ApiMatcherType.REGEX, ApiRiskLevel.LOW));
        resources.add(api(ROOM_CREATE, "Clocktower room create", "POST",
                "/api/clocktower/rooms", ApiMatcherType.EXACT, ApiRiskLevel.MEDIUM));
        resources.add(api(ROOM_MEMBERSHIP, "Clocktower room membership", "POST",
                "^/api/clocktower/rooms/[^/]+/(enter|heartbeat|join|leave|invitations/[^/]+/(accept|decline))$",
                ApiMatcherType.REGEX, ApiRiskLevel.MEDIUM));
        resources.add(api(ROOM_SEAT, "Clocktower room seats", "ANY",
                "^/api/clocktower/rooms/[^/]+/seats(/.*)?$", ApiMatcherType.REGEX, ApiRiskLevel.MEDIUM));
        resources.add(api(ROOM_GOVERNANCE, "Clocktower room governance", "ANY",
                "^/api/clocktower/rooms/[^/]+/(board|invitations|members/[^/]+/(kick|ban)|start)$",
                ApiMatcherType.REGEX, ApiRiskLevel.HIGH));
        resources.add(api(GAME_READ, "Clocktower game read", "GET",
                "^/api/clocktower/(games/[^/]+/(view|flow)|rooms/[^/]+/view)$", ApiMatcherType.REGEX,
                ApiRiskLevel.MEDIUM));
        resources.add(api(GAME_LIFECYCLE, "Clocktower game lifecycle", "POST",
                "^/api/clocktower/(rooms/[^/]+/games/(start|timeout-abort)|games/[^/]+/(end|abort))$",
                ApiMatcherType.REGEX, ApiRiskLevel.HIGH));
        resources.add(api(GAME_ACTION, "Clocktower game action", "POST",
                "^/api/clocktower/(games/[^/]+|rooms/[^/]+)/actions$", ApiMatcherType.REGEX,
                ApiRiskLevel.MEDIUM));
        resources.add(api(GAME_MIC_READ, "Clocktower game mic read", "GET",
                "^/api/clocktower/games/[^/]+/mic$", ApiMatcherType.REGEX, ApiRiskLevel.LOW));
        resources.add(api(GAME_MIC_PLAYER, "Clocktower game mic player", "POST",
                "^/api/clocktower/games/[^/]+/mic/(grab|release|turns/[^/]+/finish)$",
                ApiMatcherType.REGEX, ApiRiskLevel.MEDIUM));
        resources.add(api(GAME_MIC_STORYTELLER, "Clocktower game mic storyteller", "POST",
                "^/api/clocktower/games/[^/]+/mic/(start-day|extend|close|turns/[^/]+/(finish|skip))$",
                ApiMatcherType.REGEX, ApiRiskLevel.HIGH));
        resources.add(api(GAME_STORYTELLER, "Clocktower game storyteller", "ANY",
                "^/api/clocktower/(rooms/[^/]+/(flow(/.*)?|night-tasks/.*|nominations/.*|execution/.*|"
                        + "grimoire(/.*)?|night-checklist|storyteller/actions|rulings(/.*)?)|"
                        + "games/[^/]+/(agents(/.*)?|flow/(advance|force-advance)|night-tasks(/.*)?|"
                        + "nominations/[^/]+/close|executions/resolve))$",
                ApiMatcherType.REGEX, ApiRiskLevel.HIGH));
        resources.add(api(GAME_EVENT_STREAM, "Clocktower game event stream", "GET",
                "^/api/clocktower/(games|rooms)/[^/]+/events/stream$", ApiMatcherType.REGEX,
                ApiRiskLevel.MEDIUM));
        resources.add(api(GAME_REPLAY, "Clocktower game replay", "GET",
                "^/api/clocktower/(games/([^/]+/replay|history)|replays(/.*)?)$", ApiMatcherType.REGEX,
                ApiRiskLevel.MEDIUM));
        resources.add(api(CHAT_CONVERSATION, "Clocktower chat conversations", "ANY",
                "^/api/clocktower/(rooms/[^/]+/chat/conversations|chat/conversations)$", ApiMatcherType.REGEX,
                ApiRiskLevel.MEDIUM));
        resources.add(api(CHAT_READ, "Clocktower chat read", "GET",
                "^/api/clocktower/chat/conversations/[^/]+/messages$", ApiMatcherType.REGEX,
                ApiRiskLevel.MEDIUM));
        resources.add(api(CHAT_SEND, "Clocktower chat send", "POST",
                "^/api/clocktower/chat/conversations/[^/]+/messages$", ApiMatcherType.REGEX,
                ApiRiskLevel.MEDIUM));
        resources.add(api(CHAT_READ_STATE, "Clocktower chat read state", "POST",
                "^/api/clocktower/chat/conversations/[^/]+/read$", ApiMatcherType.REGEX,
                ApiRiskLevel.MEDIUM));
        return resources;
    }

    @Override
    public List<RbacRolePresetSeed> rolePresets() {
        return List.of(
                new RbacRolePresetSeed(APP_CODE, "CLOCKTOWER_PLAYER", "Clocktower Player",
                        "System role for Clocktower players.", 40,
                        PLAYER_PERMISSION_CODES, RbacResourceSource.PROVIDER),
                new RbacRolePresetSeed(APP_CODE, "CLOCKTOWER_STORYTELLER", "Clocktower Storyteller",
                        "System role for Clocktower storytellers.", 30,
                        STORYTELLER_PERMISSION_CODES, RbacResourceSource.PROVIDER)
        );
    }

    private static RbacResourceSeed menu(String code, String name, String parentCode, String path, int sortNo) {
        return RbacResourceSeed.menu(APP_CODE, APP_CODE, code, name, parentCode, PermissionStatus.ENABLED,
                sortNo, null, new RbacMenuSeed(code.replace("menu:", ""), path, null, null, "CrownOutlined",
                        false, true, null), RbacResourceSource.PROVIDER);
    }

    private RbacResourceSeed api(String code, String name, String method, String pattern, ApiRiskLevel riskLevel) {
        return api(code, name, method, pattern, ApiMatcherType.ANT, riskLevel);
    }

    private RbacResourceSeed api(String code, String name, String method, String pattern, ApiMatcherType matcherType,
                                 ApiRiskLevel riskLevel) {
        return api(APP_CODE, APP_CODE, code, name, method, pattern, matcherType, riskLevel);
    }

    private static RbacResourceSeed api(String appCode, String serviceTag, String code, String name, String method,
                                        String pattern, ApiMatcherType matcherType, ApiRiskLevel riskLevel) {
        return RbacResourceSeed.api(appCode, serviceTag, code, name, PermissionStatus.ENABLED, 0, null,
                new RbacApiSeed(method, pattern, matcherType, false, riskLevel), RbacResourceSource.PROVIDER);
    }

    @Component
    public static class AdminProvider implements RbacResourceProvider {

        @Override
        public String appCode() {
            return ADMIN_APP_CODE;
        }

        @Override
        public List<RbacResourceSeed> resources() {
            return List.of(
                    RbacResourceSeed.menu(ADMIN_APP_CODE, ADMIN_APP_CODE, ADMIN_AUDIT_MENU,
                            "钟楼审计", null, PermissionStatus.ENABLED, 30, null,
                            new RbacMenuSeed("clocktower:admin-audit", "/clocktower/admin/audit", null,
                                    null, "CrownOutlined", false, true, null), RbacResourceSource.PROVIDER),
                    api(ADMIN_APP_CODE, APP_CODE, ADMIN_AUDIT, "Clocktower admin audit", "GET",
                            "^/api/admin/clocktower/(audit/(summary|rooms|games|events|conversations|messages|"
                                    + "members|invitations|bans)|rooms/[^/]+/audit|games/[^/]+/audit|"
                                    + "chat/conversations/[^/]+/messages)$",
                            ApiMatcherType.REGEX, ApiRiskLevel.HIGH),
                    api(ADMIN_APP_CODE, APP_CODE, ADMIN_RULE_DATA, "Clocktower rule data administration", "ANY",
                            "^/api/admin/clocktower/(scripts(/.*)?|roles(/.*)?|rule-documents(/.*)?|"
                                    + "rule-data(/.*)?)$",
                            ApiMatcherType.REGEX, ApiRiskLevel.HIGH)
            );
        }

        @Override
        public List<RbacRolePresetSeed> rolePresets() {
            return List.of(new RbacRolePresetSeed(ADMIN_APP_CODE, "CLOCKTOWER_ADMIN", "Clocktower Admin",
                    "System role for Clocktower management operators.", 20,
                    List.of(ADMIN_AUDIT_MENU, ADMIN_AUDIT, ADMIN_RULE_DATA),
                    RbacResourceSource.PROVIDER));
        }
    }
}
