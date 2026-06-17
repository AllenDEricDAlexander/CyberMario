package top.egon.mario.clocktower.view.dto;

public record AvailableActionResponse(
        String actionType,
        String label,
        boolean enabled
) {
}
