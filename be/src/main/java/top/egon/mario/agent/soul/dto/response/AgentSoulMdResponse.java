package top.egon.mario.agent.soul.dto.response;

import java.time.Instant;

/**
 * Current user's editable Agent SoulMD document.
 */
public record AgentSoulMdResponse(
        String contentMarkdown,
        boolean enabled,
        int contentChars,
        int maxChars,
        int versionNo,
        Instant updatedAt
) {
}
