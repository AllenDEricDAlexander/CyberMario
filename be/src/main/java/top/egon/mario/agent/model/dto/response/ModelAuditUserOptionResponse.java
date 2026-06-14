package top.egon.mario.agent.model.dto.response;

/**
 * User option used by the dashboard user selector.
 */
public record ModelAuditUserOptionResponse(
        Long id,
        String username,
        String nickname
) {
}
