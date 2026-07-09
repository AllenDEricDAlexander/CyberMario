package top.egon.mario.clocktower.game.night.role;

public record AvailableTargetSpec(Long gameSeatId, String displayName, boolean selectable, String reason) {
}
