package top.egon.mario.clocktower.game.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.game.repository.ClocktowerGameSeatRepository;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomProfileRepository;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ClocktowerGameContextService {

    private static final String STATUS_IN_GAME = "IN_GAME";

    private final ClocktowerRoomProfileRepository profileRepository;
    private final ClocktowerGameSeatRepository gameSeatRepository;

    @Transactional(readOnly = true)
    public Optional<Long> currentGameId(Long roomId) {
        return profileRepository.findByRoomIdAndDeletedFalse(roomId)
                .filter(profile -> STATUS_IN_GAME.equals(profile.getStatus()))
                .map(profile -> profile.getCurrentGameId());
    }

    @Transactional(readOnly = true)
    public Long requireCurrentGameId(Long roomId) {
        return currentGameId(roomId).orElseThrow(() -> new ClocktowerException("CLOCKTOWER_GAME_NOT_RUNNING"));
    }

    @Transactional(readOnly = true)
    public Optional<Long> gameSeatId(Long gameId, Long roomSeatId) {
        return gameSeatRepository.findByGameIdAndRoomSeatIdAndDeletedFalse(gameId, roomSeatId)
                .map(seat -> seat.getId());
    }
}
