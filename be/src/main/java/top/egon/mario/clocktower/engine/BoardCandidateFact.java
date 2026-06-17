package top.egon.mario.clocktower.engine;

import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;

import java.util.List;

public record BoardCandidateFact(
        ClocktowerScriptCode scriptCode,
        int playerCount,
        List<String> roleCodes,
        int townsfolkCount,
        int outsiderCount,
        int minionCount,
        int demonCount
) {

    public int roleCount() {
        return roleCodes.size();
    }
}
