package top.egon.mario.clocktower.board;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardGenerateRequest;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardSaveRequest;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardValidateRequest;
import top.egon.mario.clocktower.board.dto.response.BoardValidationResponse;
import top.egon.mario.clocktower.board.dto.response.ClocktowerBoardCandidateResponse;
import top.egon.mario.clocktower.board.dto.response.ClocktowerBoardConfigResponse;
import top.egon.mario.clocktower.board.dto.response.ClocktowerBoardGenerateResponse;
import top.egon.mario.clocktower.board.dto.response.ClocktowerBoardValidationResponse;
import top.egon.mario.clocktower.board.dto.response.ClocktowerScoreResponse;
import top.egon.mario.clocktower.board.po.ClocktowerBoardConfigPo;
import top.egon.mario.clocktower.board.repository.ClocktowerBoardConfigRepository;
import top.egon.mario.clocktower.board.repository.ClocktowerBoardRoleRepository;
import top.egon.mario.clocktower.board.service.ClocktowerBoardService;
import top.egon.mario.clocktower.board.service.RoleMetadataProvider;
import top.egon.mario.clocktower.board.service.impl.ClocktowerBoardServiceImpl;
import top.egon.mario.clocktower.common.enums.ClocktowerAlignment;
import top.egon.mario.clocktower.common.enums.ClocktowerRoleType;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.script.dto.response.ClocktowerRoleSummaryResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    void generateTenPlayerTroubleBrewingUsesOneRolePerPlayerAndOfficialShape() {
        ClocktowerBoardGenerateResponse response = boardService.generate(new ClocktowerBoardGenerateRequest(
                ClocktowerScriptCode.TROUBLE_BREWING,
                10,
                2,
                2,
                2,
                true,
                1,
                List.of(),
                List.of(),
                "seed-10"
        ), principal(1L));

        ClocktowerBoardCandidateResponse candidate = response.candidates().getFirst();

        assertThat(candidate.roleCodes()).hasSize(10);
        assertThat(candidate.validation().valid()).isTrue();
        assertThat(candidate.validation().roleTypeCounts())
                .containsEntry("TOWNSFOLK", 7)
                .containsEntry("OUTSIDER", 0)
                .containsEntry("MINION", 2)
                .containsEntry("DEMON", 1);
    }

    @Test
    void generateUsesPreferenceParametersWhenOrderingCandidates() {
        ClocktowerBoardGenerateRequest easyRequest = new ClocktowerBoardGenerateRequest(
                ClocktowerScriptCode.TROUBLE_BREWING,
                10,
                1,
                1,
                1,
                true,
                1,
                List.of(),
                List.of(),
                "same-seed"
        );
        ClocktowerBoardGenerateRequest hardRequest = new ClocktowerBoardGenerateRequest(
                ClocktowerScriptCode.TROUBLE_BREWING,
                10,
                5,
                5,
                5,
                false,
                1,
                List.of(),
                List.of(),
                "same-seed"
        );

        ClocktowerBoardCandidateResponse easy = boardService.generate(easyRequest, principal(1L))
                .candidates().getFirst();
        ClocktowerBoardCandidateResponse hard = boardService.generate(hardRequest, principal(1L))
                .candidates().getFirst();

        assertThat(hard.roleCodes()).isNotEqualTo(easy.roleCodes());
        assertThat(hard.scores()).extracting(ClocktowerScoreResponse::scoreType)
                .contains("difficulty", "chaos", "evilPressure");
    }

    @Test
    void generateReturnsDistinctCandidatesWhenAlternativesAreAvailable() {
        ClocktowerBoardGenerateResponse response = boardService.generate(new ClocktowerBoardGenerateRequest(
                ClocktowerScriptCode.TROUBLE_BREWING,
                10,
                2,
                2,
                2,
                true,
                3,
                List.of(),
                List.of(),
                "same-seed"
        ), principal(1L));

        assertThat(response.candidates()).hasSize(3);
        assertThat(response.candidates().stream()
                .map(candidate -> Set.copyOf(candidate.roleCodes()))
                .toList()).doesNotHaveDuplicates();
    }

    @Test
    void saveBoardConfigResponsePreservesRoleCodesAndAddsRoleSummaries() {
        RoleMetadataProvider provider = scriptCode -> List.of(
                new ClocktowerRoleSummaryResponse(scriptCode, "CHEF", "厨师", ClocktowerRoleType.TOWNSFOLK,
                        ClocktowerAlignment.GOOD),
                new ClocktowerRoleSummaryResponse(scriptCode, "POISONER", "投毒者", ClocktowerRoleType.MINION,
                        ClocktowerAlignment.EVIL));
        ClocktowerBoardConfigRepository configRepository = mock(ClocktowerBoardConfigRepository.class);
        ClocktowerBoardRoleRepository roleRepository = mock(ClocktowerBoardRoleRepository.class);
        when(configRepository.save(any(ClocktowerBoardConfigPo.class))).thenAnswer(invocation -> {
            ClocktowerBoardConfigPo config = invocation.getArgument(0);
            assertThat(config.isValid()).isTrue();
            config.setId(42L);
            config.setValid(true);
            config.setCreatedAt(java.time.Instant.parse("2026-06-19T00:00:00Z"));
            return config;
        });
        ClocktowerBoardService service = new ClocktowerBoardServiceImpl(provider,
                ClocktowerBoardTestFactory.ruleEngine(), configRepository, roleRepository, new ObjectMapper());
        ClocktowerBoardValidationResponse validation = new ClocktowerBoardValidationResponse(true, Map.of(),
                List.of(), List.of());
        ClocktowerBoardSaveRequest request = new ClocktowerBoardSaveRequest(ClocktowerScriptCode.TROUBLE_BREWING,
                2, 1, 1, 1, true, "seed", List.of("CHEF", "UNKNOWN"), validation);

        ClocktowerBoardConfigResponse response = service.save(request, principal(1L));

        assertThat(response.boardId()).isEqualTo(42L);
        assertThat(response.valid()).isTrue();
        assertThat(response.createdAt()).isEqualTo(java.time.Instant.parse("2026-06-19T00:00:00Z"));
        assertThat(response.roleCodes()).containsExactly("CHEF", "UNKNOWN");
        assertThat(response.roles()).extracting(role -> role.roleName())
                .containsExactly("厨师", "UNKNOWN");
    }

    @Test
    void saveBoardConfigTreatsMissingValidationAsInvalid() {
        RoleMetadataProvider provider = scriptCode -> List.of(
                new ClocktowerRoleSummaryResponse(scriptCode, "CHEF", "厨师", ClocktowerRoleType.TOWNSFOLK,
                        ClocktowerAlignment.GOOD));
        ClocktowerBoardConfigRepository configRepository = mock(ClocktowerBoardConfigRepository.class);
        ClocktowerBoardRoleRepository roleRepository = mock(ClocktowerBoardRoleRepository.class);
        when(configRepository.save(any(ClocktowerBoardConfigPo.class))).thenAnswer(invocation -> {
            ClocktowerBoardConfigPo config = invocation.getArgument(0);
            assertThat(config.isValid()).isFalse();
            config.setId(43L);
            return config;
        });
        ClocktowerBoardService service = new ClocktowerBoardServiceImpl(provider,
                ClocktowerBoardTestFactory.ruleEngine(), configRepository, roleRepository, new ObjectMapper());
        ClocktowerBoardSaveRequest request = new ClocktowerBoardSaveRequest(ClocktowerScriptCode.TROUBLE_BREWING,
                1, 1, 1, 1, true, "seed", List.of("CHEF"), null);

        ClocktowerBoardConfigResponse response = assertDoesNotThrow(() -> service.save(request, principal(1L)));

        assertThat(response.valid()).isFalse();
    }

    @Test
    void boardValidationRoleTypeCountsUseStableJsonKeys() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ClocktowerBoardValidationResponse response = new ClocktowerBoardValidationResponse(true,
                Map.of("TOWNSFOLK", 3, "MINION", 1), List.of(), List.of());

        String json = mapper.writeValueAsString(response);

        assertThat(json).contains("\"TOWNSFOLK\":3");
        assertThat(json).doesNotContain("镇民");
        ClocktowerBoardValidationResponse legacy = mapper.readValue("""
                {"valid":true,"roleTypeCounts":{"TOWNSFOLK":3,"MINION":1},"violations":[],"scores":[]}
                """, ClocktowerBoardValidationResponse.class);
        assertThat(legacy.roleTypeCounts()).containsEntry("TOWNSFOLK", 3)
                .containsEntry("MINION", 1);
    }

    private static RbacPrincipal principal(Long userId) {
        return new RbacPrincipal(userId, "user-" + userId, Set.of("CLOCKTOWER_STORYTELLER"), Set.of(), "v1");
    }
}
