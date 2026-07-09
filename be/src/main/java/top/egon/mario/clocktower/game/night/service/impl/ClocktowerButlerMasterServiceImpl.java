package top.egon.mario.clocktower.game.night.service.impl;

import org.springframework.stereotype.Service;
import top.egon.mario.clocktower.game.night.service.ClocktowerButlerMasterService;

import java.util.Optional;

@Service
public class ClocktowerButlerMasterServiceImpl implements ClocktowerButlerMasterService {

    @Override
    public Optional<Long> currentMasterGameSeatId(Long gameId, Long butlerGameSeatId) {
        return Optional.empty();
    }
}
