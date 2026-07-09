package top.egon.mario.clocktower.game.night.service;

import java.util.Optional;

public interface ClocktowerButlerMasterService {

    Optional<Long> currentMasterGameSeatId(Long gameId, Long butlerGameSeatId);
}
