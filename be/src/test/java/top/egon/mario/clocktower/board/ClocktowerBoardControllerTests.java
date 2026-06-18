package top.egon.mario.clocktower.board;

import org.junit.jupiter.api.Test;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardGenerateRequest;
import top.egon.mario.clocktower.board.dto.response.ClocktowerBoardGenerateResponse;
import top.egon.mario.clocktower.board.service.ClocktowerBoardService;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ClocktowerBoardControllerTests {

    private final ClocktowerBoardService boardService = ClocktowerBoardTestFactory.service();

    @Test
    void generateBoardReturnsRequestedCandidateCount() {
        ClocktowerBoardGenerateRequest request = new ClocktowerBoardGenerateRequest(
                ClocktowerScriptCode.TROUBLE_BREWING, 5, 2, 2, 2, true, 2, List.of(), List.of(), "seed-1");

        ClocktowerBoardGenerateResponse response = boardService.generate(request, principal(1L));

        assertThat(response.candidates()).hasSize(2);
        assertThat(response.candidates()).allSatisfy(candidate -> {
            assertThat(candidate.playerCount()).isEqualTo(5);
            assertThat(candidate.validation().valid()).isTrue();
            assertThat(candidate.roleCodes()).doesNotContain("BMR_TOWNSFOLK", "BMR_MINION", "BMR_DEMON");
            assertThat(candidate.roles()).extracting(role -> role.roleCode())
                    .containsExactlyElementsOf(candidate.roleCodes());
        });
    }

    private static RbacPrincipal principal(Long userId) {
        return new RbacPrincipal(userId, "user-" + userId, Set.of("CLOCKTOWER_STORYTELLER"), Set.of(), "v1");
    }
}
