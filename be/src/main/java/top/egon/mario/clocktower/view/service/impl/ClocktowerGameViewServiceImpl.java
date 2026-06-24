package top.egon.mario.clocktower.view.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatConversationResponse;
import top.egon.mario.clocktower.chat.service.ClocktowerChatService;
import top.egon.mario.clocktower.common.enums.ClocktowerViewerMode;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;
import top.egon.mario.clocktower.game.repository.ClocktowerGameEventRepository;
import top.egon.mario.clocktower.game.repository.ClocktowerGameSeatRepository;
import top.egon.mario.clocktower.view.dto.AvailableActionResponse;
import top.egon.mario.clocktower.view.dto.ClocktowerGameEventResponse;
import top.egon.mario.clocktower.view.dto.ClocktowerGameSeatViewResponse;
import top.egon.mario.clocktower.view.dto.ClocktowerGameViewResponse;
import top.egon.mario.clocktower.view.service.ClocktowerGameProjectionMapper;
import top.egon.mario.clocktower.view.service.ClocktowerGameViewService;
import top.egon.mario.clocktower.view.service.ClocktowerViewerContext;
import top.egon.mario.clocktower.view.service.ClocktowerViewerResolver;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ClocktowerGameViewServiceImpl implements ClocktowerGameViewService {

    private final ClocktowerViewerResolver viewerResolver;
    private final ClocktowerGameSeatRepository gameSeatRepository;
    private final ClocktowerGameEventRepository gameEventRepository;
    private final ClocktowerChatService chatService;
    private final ClocktowerGameProjectionMapper projectionMapper;

    @Override
    @Transactional(readOnly = true)
    public ClocktowerGameViewResponse gameView(Long gameId, RbacPrincipal principal) {
        ClocktowerViewerContext viewer = viewerResolver.resolveGameViewer(gameId, principal);
        List<ClocktowerGameSeatPo> seats = gameSeatRepository.findByGameIdAndDeletedFalseOrderBySeatNoAsc(gameId);
        List<ClocktowerGameSeatViewResponse> publicSeats = seats.stream()
                .map(ClocktowerGameSeatViewResponse::publicView)
                .toList();
        List<ClocktowerGameSeatViewResponse> grimoire = viewer.viewerMode() == ClocktowerViewerMode.STORYTELLER
                ? seats.stream().map(ClocktowerGameSeatViewResponse::fullView).toList()
                : List.of();
        List<ClocktowerChatConversationResponse> conversations =
                viewer.viewerMode() == ClocktowerViewerMode.INVITED
                        ? List.of()
                        : chatService.conversationsForGame(viewer.game().getRoomId(), viewer.game().getId(),
                                principal);
        return new ClocktowerGameViewResponse(viewer.game().getId(), viewer.game().getRoomId(),
                viewer.game().getGameNo(), viewer.game().getStatus(), viewer.game().getPhase(), viewer.viewerMode(),
                mySeat(viewer), publicSeats, grimoire, availableActions(viewer), events(viewer), conversations);
    }

    private ClocktowerGameSeatViewResponse mySeat(ClocktowerViewerContext viewer) {
        return viewer.viewerMode() == ClocktowerViewerMode.PLAYER
                ? ClocktowerGameSeatViewResponse.fullView(viewer.gameSeat())
                : null;
    }

    private List<AvailableActionResponse> availableActions(ClocktowerViewerContext viewer) {
        if (viewer.viewerMode() != ClocktowerViewerMode.PLAYER) {
            return List.of();
        }
        return switch (viewer.game().getPhase()) {
            case "DAY" -> List.of(
                    new AvailableActionResponse("PUBLIC_SPEECH", "公开发言", true),
                    new AvailableActionResponse("NOMINATE", "提名", true)
            );
            case "NOMINATION" -> List.of(
                    new AvailableActionResponse("PUBLIC_SPEECH", "公开发言", true),
                    new AvailableActionResponse("VOTE", "投票", true)
            );
            case "FIRST_NIGHT", "NIGHT" -> List.of(new AvailableActionResponse("NIGHT_CHOICE", "夜晚行动", true));
            default -> List.of();
        };
    }

    private List<ClocktowerGameEventResponse> events(ClocktowerViewerContext viewer) {
        return gameEventRepository.findByGameIdAndStatusAndDeletedFalseOrderByEventSeqAsc(
                        viewer.game().getId(), "VISIBLE")
                .stream()
                .map(projectionMapper::toEventResponse)
                .filter(event -> projectionMapper.visibleTo(event, viewer, false))
                .toList();
    }
}
