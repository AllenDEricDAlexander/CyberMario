package top.egon.mario.agent.model.dto.response;

/**
 * Aggregated model usage for one user.
 */
public record ModelAuditUserStatResponse(
        Long userId,
        String username,
        String nickname,
        long callCount,
        long totalTokens
) {
}
