package top.egon.mario.clocktower.ruling.dto;

public record ClocktowerRulingUndoRequest(
        String note,
        boolean force
) {
}
