package top.egon.mario.agent.soul.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Current-user request for replacing Agent SoulMD and its injection switch.
 */
public record AgentSoulMdUpdateRequest(
        @NotNull(message = "SoulMD content is required")
        @Size(max = 50000, message = "SoulMD must be at most 50000 characters")
        String contentMarkdown,
        @NotNull(message = "SoulMD enabled flag is required")
        Boolean enabled
) {
}
