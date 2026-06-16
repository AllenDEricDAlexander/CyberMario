package top.egon.mario.agent.service.resource;

import java.util.List;

/**
 * Permission codes for current-user Agent memory management.
 */
public final class AgentMemoryPermissionCatalog {

    public static final String APP_CODE = "agent";
    public static final String ROLE_CHAT_BASIC = "CHAT_BASIC";
    public static final String ROLE_RAG_USER = "RAG_USER";
    public static final String MENU_MEMORY = "menu:agent:memory";
    public static final String MENU_MEMORY_ARCHIVE = "menu:agent:memory-archive";
    public static final String BTN_SWITCH = "btn:agent:memory:switch";
    public static final String BTN_ARCHIVE = "btn:agent:memory:archive";
    public static final String BTN_RESTORE = "btn:agent:memory:restore";
    public static final String BTN_DELETE = "btn:agent:memory:delete";
    public static final String BTN_RELEASE = "btn:agent:memory:release";
    public static final String API_SESSION_COLLECTION = "api:agent:memory:session:collection";
    public static final String API_SESSION_ALL = "api:agent:memory:session:*";
    public static final String API_MESSAGE_READ = "api:agent:memory:message:read";
    public static final String API_LONG_TERM_READ = "api:agent:memory:long-term:read";
    public static final String API_LONG_TERM_VERSION = "api:agent:memory:long-term:version";
    public static final String API_EXTRACTION_READ = "api:agent:memory:extraction:read";

    public static final List<String> CHAT_BASIC_PERMISSION_CODES = List.of(
            MENU_MEMORY,
            MENU_MEMORY_ARCHIVE,
            BTN_SWITCH,
            BTN_ARCHIVE,
            BTN_RESTORE,
            BTN_DELETE,
            BTN_RELEASE,
            API_SESSION_COLLECTION,
            API_SESSION_ALL,
            API_MESSAGE_READ,
            API_LONG_TERM_READ,
            API_LONG_TERM_VERSION,
            API_EXTRACTION_READ
    );

    public static final List<String> RAG_USER_PERMISSION_CODES = List.of(
            BTN_SWITCH,
            BTN_ARCHIVE,
            BTN_RESTORE,
            BTN_DELETE,
            BTN_RELEASE,
            API_SESSION_COLLECTION,
            API_SESSION_ALL,
            API_MESSAGE_READ
    );

    private AgentMemoryPermissionCatalog() {
    }
}
