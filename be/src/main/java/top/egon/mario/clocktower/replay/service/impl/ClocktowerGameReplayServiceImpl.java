package top.egon.mario.clocktower.replay.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.common.enums.ClocktowerViewerMode;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.clocktower.game.repository.ClocktowerGameEventRepository;
import top.egon.mario.clocktower.game.repository.ClocktowerGameRepository;
import top.egon.mario.clocktower.game.repository.ClocktowerGameSeatRepository;
import top.egon.mario.clocktower.replay.dto.ClocktowerGameHistoryResponse;
import top.egon.mario.clocktower.replay.dto.ClocktowerGameReplayResponse;
import top.egon.mario.clocktower.replay.service.ClocktowerGameReplayService;
import top.egon.mario.clocktower.room.policy.ClocktowerRoomMutationPolicy;
import top.egon.mario.clocktower.view.service.ClocktowerGameProjectionMapper;
import top.egon.mario.clocktower.view.service.ClocktowerViewerContext;
import top.egon.mario.clocktower.view.service.ClocktowerViewerResolver;
import top.egon.mario.rbac.service.security.RbacPrincipal;
import top.egon.mario.room.repository.RoomSpaceRepository;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ClocktowerGameReplayServiceImpl implements ClocktowerGameReplayService {

    private static final Set<String> HISTORY_STATUSES = Set.of("ENDED", "ABORTED", "ARCHIVED");

    private final ClocktowerViewerResolver viewerResolver;
    private final ClocktowerGameRepository gameRepository;
    private final ClocktowerGameSeatRepository gameSeatRepository;
    private final ClocktowerGameEventRepository gameEventRepository;
    private final RoomSpaceRepository roomRepository;
    private final ClocktowerGameProjectionMapper projectionMapper;

    @Override
    @Transactional(readOnly = true)
    public ClocktowerGameReplayResponse replay(Long gameId, RbacPrincipal principal) {
        ClocktowerViewerContext viewer = replayViewer(gameId, principal);
        if (viewer.viewerMode() != ClocktowerViewerMode.STORYTELLER
                && viewer.viewerMode() != ClocktowerViewerMode.PLAYER) {
            throw new ClocktowerException("CLOCKTOWER_REPLAY_FORBIDDEN");
        }
        return new ClocktowerGameReplayResponse(viewer.game().getId(), viewer.game().getRoomId(),
                viewer.viewerMode(), gameEventRepository.findByGameIdAndStatusAndDeletedFalseOrderByEventSeqAsc(
                                gameId, "VISIBLE")
                        .stream()
                        .map(projectionMapper::toEventResponse)
                        .filter(event -> projectionMapper.visibleTo(event, viewer, false))
                        .toList());
    }

    private ClocktowerViewerContext replayViewer(Long gameId, RbacPrincipal principal) {
        try {
            return viewerResolver.resolveGameViewer(gameId, principal);
        } catch (ClocktowerException ex) {
            if ("CLOCKTOWER_VIEW_FORBIDDEN".equals(ex.getMessage())) {
                throw new ClocktowerException("CLOCKTOWER_REPLAY_FORBIDDEN");
            }
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClocktowerGameHistoryResponse> history(RbacPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new ClocktowerException("CLOCKTOWER_AUTH_REQUIRED");
        }
        Map<Long, ClocktowerGamePo> gamesById = new LinkedHashMap<>();
        List<Long> playerGameIds = gameSeatRepository.findByUserIdAndDeletedFalseOrderByIdDesc(
                        principal.userId())
                .stream()
                .map(seat -> seat.getGameId())
                .distinct()
                .toList();
        if (!playerGameIds.isEmpty()) {
            gameRepository.findByIdInAndDeletedFalse(playerGameIds).forEach(game -> gamesById.put(game.getId(), game));
        }
        List<Long> ownerRoomIds = roomRepository.findByContextTypeAndOwnerUserIdAndDeletedFalseOrderByLastActiveAtDescIdDesc(
                        ClocktowerRoomMutationPolicy.CONTEXT_TYPE, principal.userId())
                .stream()
                .map(room -> room.getId())
                .toList();
        if (!ownerRoomIds.isEmpty()) {
            gameRepository.findByRoomIdInAndDeletedFalseOrderByStartedAtDescIdDesc(ownerRoomIds)
                    .forEach(game -> gamesById.put(game.getId(), game));
        }
        return gamesById.values().stream()
                .filter(game -> HISTORY_STATUSES.contains(game.getStatus()))
                .sorted(Comparator.comparing(ClocktowerGamePo::getStartedAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ClocktowerGamePo::getId, Comparator.reverseOrder()))
                .map(ClocktowerGameHistoryResponse::from)
                .toList();
    }
}
