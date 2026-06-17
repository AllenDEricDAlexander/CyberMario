package top.egon.mario.clocktower.board;

import org.junit.jupiter.api.Test;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardValidateRequest;
import top.egon.mario.clocktower.board.dto.response.BoardValidationResponse;
import top.egon.mario.clocktower.board.service.ClocktowerBoardService;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClocktowerBoardServiceTests {

    private final ClocktowerBoardService boardService = ClocktowerBoardTestFactory.service();

    @Test
    void validateRejectsTroubleBrewingWithTooFewPlayers() {
        BoardValidationResponse response = boardService.validate(new ClocktowerBoardValidateRequest(
                ClocktowerScriptCode.TROUBLE_BREWING,
                4,
                List.of("EMPATH", "IMP", "CHEF", "MONK")
        ));

        assertThat(response.valid()).isFalse();
        assertThat(response.issues()).extracting(issue -> issue.code())
                .contains("BOARD_PLAYER_COUNT_TOO_LOW");
    }

    @Test
    void validateAcceptsFivePlayerTroubleBrewingShape() {
        BoardValidationResponse response = boardService.validate(new ClocktowerBoardValidateRequest(
                ClocktowerScriptCode.TROUBLE_BREWING,
                5,
                List.of("EMPATH", "CHEF", "MONK", "POISONER", "IMP")
        ));

        assertThat(response.valid()).isTrue();
        assertThat(response.typeCounts().townsfolk()).isEqualTo(3);
        assertThat(response.typeCounts().minion()).isEqualTo(1);
        assertThat(response.typeCounts().demon()).isEqualTo(1);
    }
}
