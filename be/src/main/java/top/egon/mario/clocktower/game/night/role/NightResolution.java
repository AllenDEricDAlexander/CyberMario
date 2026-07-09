package top.egon.mario.clocktower.game.night.role;

import java.util.List;
import java.util.Map;

public record NightResolution(
        Map<String, Object> result,
        List<Map<String, Object>> privateInfos,
        List<Map<String, Object>> storytellerEvents,
        List<Map<String, Object>> publicEvents,
        String status
) {

    public static NightResolution done(Map<String, Object> result) {
        return new NightResolution(result, List.of(), List.of(), List.of(), "DONE");
    }
}
