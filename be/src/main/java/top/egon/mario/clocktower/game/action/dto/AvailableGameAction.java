package top.egon.mario.clocktower.game.action.dto;

public record AvailableGameAction(
        String actionType,
        String label,
        boolean enabled,
        String disabledReason
) {
}
