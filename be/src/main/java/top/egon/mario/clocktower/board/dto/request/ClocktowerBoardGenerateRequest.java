package top.egon.mario.clocktower.board.dto.request;

import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;

import java.util.List;

public record ClocktowerBoardGenerateRequest(
        ClocktowerScriptCode scriptCode,
        int playerCount,
        int difficulty,
        int chaos,
        int evilPressure,
        boolean newbieFriendly,
        int candidateCount,
        List<String> lockedRoleCodes,
        List<String> bannedRoleCodes,
        String seed
) {
}
