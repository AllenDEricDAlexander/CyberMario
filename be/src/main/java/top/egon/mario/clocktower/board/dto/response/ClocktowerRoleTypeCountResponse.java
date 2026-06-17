package top.egon.mario.clocktower.board.dto.response;

public record ClocktowerRoleTypeCountResponse(
        int townsfolk,
        int outsider,
        int minion,
        int demon,
        int traveler,
        int fabled
) {
}
