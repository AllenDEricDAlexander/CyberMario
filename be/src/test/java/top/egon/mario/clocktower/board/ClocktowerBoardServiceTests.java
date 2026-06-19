package top.egon.mario.clocktower.board;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardGenerateRequest;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardQuery;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardSaveRequest;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardValidateRequest;
import top.egon.mario.clocktower.board.dto.response.BoardValidationResponse;
import top.egon.mario.clocktower.board.dto.response.ClocktowerBoardCandidateResponse;
import top.egon.mario.clocktower.board.dto.response.ClocktowerBoardConfigResponse;
import top.egon.mario.clocktower.board.dto.response.ClocktowerBoardGenerateResponse;
import top.egon.mario.clocktower.board.dto.response.ClocktowerBoardValidationResponse;
import top.egon.mario.clocktower.board.dto.response.ClocktowerRuleViolationResponse;
import top.egon.mario.clocktower.board.dto.response.ClocktowerScoreResponse;
import top.egon.mario.clocktower.board.po.ClocktowerBoardRolePo;
import top.egon.mario.clocktower.board.po.ClocktowerBoardConfigPo;
import top.egon.mario.clocktower.board.repository.ClocktowerBoardConfigRepository;
import top.egon.mario.clocktower.board.repository.ClocktowerBoardRoleRepository;
import top.egon.mario.clocktower.common.ClocktowerException;
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
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    void validateRejectsUnknownRoleCode() {
        BoardValidationResponse response = boardService.validate(new ClocktowerBoardValidateRequest(
                ClocktowerScriptCode.TROUBLE_BREWING,
                5,
                List.of("EMPATH", "CHEF", "MONK", "POISONER", "NO_SUCH_ROLE")
        ));

        assertThat(response.valid()).isFalse();
        assertThat(response.issues()).extracting(ClocktowerRuleViolationResponse::code)
                .contains("BOARD_ROLE_NOT_FOUND");
    }

    @Test
    void validateReportsEachUnknownRoleCode() {
        BoardValidationResponse response = boardService.validate(new ClocktowerBoardValidateRequest(
                ClocktowerScriptCode.TROUBLE_BREWING,
                5,
                List.of("EMPATH", "CHEF", "MONK", "NO_SUCH_ROLE", "NO_SUCH_OTHER_ROLE")
        ));

        assertThat(response.valid()).isFalse();
        assertThat(response.issues())
                .filteredOn(issue -> "BOARD_ROLE_NOT_FOUND".equals(issue.code()))
                .extracting(ClocktowerRuleViolationResponse::message)
                .containsExactly("角色不存在或未启用：NO_SUCH_ROLE", "角色不存在或未启用：NO_SUCH_OTHER_ROLE");
    }

    @Test
    void validateRejectsDuplicateRoleCode() {
        BoardValidationResponse response = boardService.validate(new ClocktowerBoardValidateRequest(
                ClocktowerScriptCode.TROUBLE_BREWING,
                5,
                List.of("EMPATH", "CHEF", "CHEF", "POISONER", "IMP")
        ));

        assertThat(response.valid()).isFalse();
        assertThat(response.issues()).extracting(ClocktowerRuleViolationResponse::code)
                .contains("BOARD_ROLE_DUPLICATED");
        assertThat(response.issues()).extracting(ClocktowerRuleViolationResponse::message)
                .contains("角色不能重复：CHEF");
    }

    @Test
    void validateRejectsRoleFromAnotherScript() {
        BoardValidationResponse response = boardService.validate(new ClocktowerBoardValidateRequest(
                ClocktowerScriptCode.TROUBLE_BREWING,
                5,
                List.of("EMPATH", "CHEF", "MONK", "POISONER", "BMR_DEMON")
        ));

        assertThat(response.valid()).isFalse();
        assertThat(response.issues()).extracting(ClocktowerRuleViolationResponse::code)
                .contains("BOARD_ROLE_SCRIPT_MISMATCH");
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
        RoleMetadataProvider provider = new RoleMetadataProvider() {
            @Override
            public List<ClocktowerRoleSummaryResponse> roles(ClocktowerScriptCode scriptCode) {
                return List.of(
                        new ClocktowerRoleSummaryResponse(scriptCode, "CHEF", "厨师", ClocktowerRoleType.TOWNSFOLK,
                                ClocktowerAlignment.GOOD),
                        new ClocktowerRoleSummaryResponse(scriptCode, "POISONER", "投毒者", ClocktowerRoleType.MINION,
                                ClocktowerAlignment.EVIL));
            }

            @Override
            public List<ClocktowerRoleSummaryResponse> enabledRoles(java.util.Collection<String> roleCodes) {
                return roles(ClocktowerScriptCode.TROUBLE_BREWING).stream()
                        .filter(role -> roleCodes.contains(role.roleCode()))
                        .toList();
            }
        };
        ClocktowerBoardConfigRepository configRepository = mock(ClocktowerBoardConfigRepository.class);
        ClocktowerBoardRoleRepository roleRepository = mock(ClocktowerBoardRoleRepository.class);
        when(configRepository.save(any(ClocktowerBoardConfigPo.class))).thenAnswer(invocation -> {
            ClocktowerBoardConfigPo config = invocation.getArgument(0);
            assertThat(config.isValid()).isFalse();
            config.setId(42L);
            config.setValid(false);
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
        assertThat(response.valid()).isFalse();
        assertThat(response.createdAt()).isEqualTo(java.time.Instant.parse("2026-06-19T00:00:00Z"));
        assertThat(response.roleCodes()).containsExactly("CHEF", "UNKNOWN");
        assertThat(response.roles()).extracting(role -> role.roleName())
                .containsExactly("厨师", "UNKNOWN");
    }

    @Test
    void saveRevalidatesAndPersistsBackendValidationResult() {
        RoleMetadataProvider provider = ClocktowerBoardTestFactory.roleMetadataProvider();
        ClocktowerBoardConfigRepository configRepository = mock(ClocktowerBoardConfigRepository.class);
        ClocktowerBoardRoleRepository roleRepository = mock(ClocktowerBoardRoleRepository.class);
        when(configRepository.save(any(ClocktowerBoardConfigPo.class))).thenAnswer(invocation -> {
            ClocktowerBoardConfigPo config = invocation.getArgument(0);
            config.setId(99L);
            config.setCreatedAt(java.time.Instant.parse("2026-06-19T01:00:00Z"));
            return config;
        });
        ClocktowerBoardService service = new ClocktowerBoardServiceImpl(provider,
                ClocktowerBoardTestFactory.ruleEngine(), configRepository, roleRepository, new ObjectMapper());
        ClocktowerBoardValidationResponse trustedFrontendValidation = new ClocktowerBoardValidationResponse(true,
                Map.of(), List.of(), List.of());
        ClocktowerBoardSaveRequest request = new ClocktowerBoardSaveRequest(ClocktowerScriptCode.TROUBLE_BREWING,
                5, 1, 1, 1, true, "seed", List.of("EMPATH", "CHEF"), trustedFrontendValidation);

        ClocktowerBoardConfigResponse response = service.save(request, principal(1L));

        assertThat(response.valid()).isFalse();
        assertThat(response.validation().valid()).isFalse();
        assertThat(response.validation().violations()).extracting(ClocktowerRuleViolationResponse::code)
                .contains("BOARD_ROLE_COUNT_MISMATCH");
    }

    @Test
    void saveRevalidatesAndPersistsDuplicateRoleIssue() {
        RoleMetadataProvider provider = ClocktowerBoardTestFactory.roleMetadataProvider();
        ClocktowerBoardConfigRepository configRepository = mock(ClocktowerBoardConfigRepository.class);
        ClocktowerBoardRoleRepository roleRepository = mock(ClocktowerBoardRoleRepository.class);
        when(configRepository.save(any(ClocktowerBoardConfigPo.class))).thenAnswer(invocation -> {
            ClocktowerBoardConfigPo config = invocation.getArgument(0);
            assertThat(config.isValid()).isFalse();
            config.setId(100L);
            config.setCreatedAt(java.time.Instant.parse("2026-06-19T02:00:00Z"));
            return config;
        });
        ClocktowerBoardService service = new ClocktowerBoardServiceImpl(provider,
                ClocktowerBoardTestFactory.ruleEngine(), configRepository, roleRepository, new ObjectMapper());
        ClocktowerBoardValidationResponse trustedFrontendValidation = new ClocktowerBoardValidationResponse(true,
                Map.of(), List.of(), List.of());
        ClocktowerBoardSaveRequest request = new ClocktowerBoardSaveRequest(ClocktowerScriptCode.TROUBLE_BREWING,
                5, 1, 1, 1, true, "seed", List.of("EMPATH", "CHEF", "CHEF", "POISONER", "IMP"),
                trustedFrontendValidation);

        ClocktowerBoardConfigResponse response = service.save(request, principal(1L));

        assertThat(response.valid()).isFalse();
        assertThat(response.validation().valid()).isFalse();
        assertThat(response.validation().violations()).extracting(ClocktowerRuleViolationResponse::code)
                .contains("BOARD_ROLE_DUPLICATED");
    }

    @Test
    void saveBindsCurrentPrincipalAsBoardOwner() {
        RoleMetadataProvider provider = ClocktowerBoardTestFactory.roleMetadataProvider();
        ClocktowerBoardConfigRepository configRepository = mock(ClocktowerBoardConfigRepository.class);
        ClocktowerBoardRoleRepository roleRepository = mock(ClocktowerBoardRoleRepository.class);
        RbacPrincipal principal = principal(11L);
        ArgumentCaptor<ClocktowerBoardConfigPo> configCaptor = ArgumentCaptor.forClass(ClocktowerBoardConfigPo.class);
        when(configRepository.save(any(ClocktowerBoardConfigPo.class))).thenAnswer(invocation -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(principal);
            ClocktowerBoardConfigPo config = invocation.getArgument(0);
            config.setId(101L);
            config.setCreatedAt(java.time.Instant.parse("2026-06-19T02:30:00Z"));
            return config;
        });
        ClocktowerBoardService service = new ClocktowerBoardServiceImpl(provider,
                ClocktowerBoardTestFactory.ruleEngine(), configRepository, roleRepository, new ObjectMapper());
        ClocktowerBoardValidationResponse trustedFrontendValidation = new ClocktowerBoardValidationResponse(true,
                Map.of(), List.of(), List.of());
        ClocktowerBoardSaveRequest request = new ClocktowerBoardSaveRequest(ClocktowerScriptCode.TROUBLE_BREWING,
                5, 1, 1, 1, true, "seed",
                List.of("EMPATH", "CHEF", "MONK", "POISONER", "IMP"), trustedFrontendValidation);

        service.save(request, principal);

        verify(configRepository).save(configCaptor.capture());
        assertThat(configCaptor.getValue().getCreatedBy()).isEqualTo(11L);
        assertThat(configCaptor.getValue().getUpdatedBy()).isEqualTo(11L);
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
    void listRejectsAnonymousPrincipal() {
        ClocktowerBoardConfigRepository configRepository = mock(ClocktowerBoardConfigRepository.class);
        ClocktowerBoardRoleRepository roleRepository = mock(ClocktowerBoardRoleRepository.class);
        ClocktowerBoardService service = new ClocktowerBoardServiceImpl(ClocktowerBoardTestFactory.roleMetadataProvider(),
                ClocktowerBoardTestFactory.ruleEngine(), configRepository, roleRepository, new ObjectMapper());

        assertThatThrownBy(() -> service.list(new ClocktowerBoardQuery(
                ClocktowerScriptCode.TROUBLE_BREWING, 5, true), PageRequest.of(0, 20), null))
                .isInstanceOf(ClocktowerException.class)
                .hasMessage("CLOCKTOWER_AUTH_REQUIRED");
        verify(configRepository, never()).findAll(any(Specification.class), any(PageRequest.class));
    }

    @Test
    void listFiltersByCurrentUserAndQueryAndMapsValidationJson() {
        RoleMetadataProvider provider = ClocktowerBoardTestFactory.roleMetadataProvider();
        ClocktowerBoardConfigRepository configRepository = mock(ClocktowerBoardConfigRepository.class);
        ClocktowerBoardRoleRepository roleRepository = mock(ClocktowerBoardRoleRepository.class);
        ClocktowerBoardConfigPo config = boardConfig(77L, "CTB-OWNED", ClocktowerScriptCode.TROUBLE_BREWING,
                5, 1L, true);
        when(configRepository.findAll(any(Specification.class), eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(config), PageRequest.of(0, 20), 1));
        when(roleRepository.findByBoardConfigIdAndDeletedFalseOrderBySortOrderAsc(77L))
                .thenReturn(List.of(boardRole("EMPATH", 1), boardRole("CHEF", 2), boardRole("MONK", 3),
                        boardRole("POISONER", 4), boardRole("IMP", 5)));
        ClocktowerBoardService service = new ClocktowerBoardServiceImpl(provider,
                ClocktowerBoardTestFactory.ruleEngine(), configRepository, roleRepository, new ObjectMapper());

        Page<ClocktowerBoardConfigResponse> response = service.list(new ClocktowerBoardQuery(
                ClocktowerScriptCode.TROUBLE_BREWING, 5, true), PageRequest.of(0, 20), principal(1L));

        assertThat(response.getTotalElements()).isEqualTo(1);
        ClocktowerBoardConfigResponse board = response.getContent().getFirst();
        assertThat(board.boardId()).isEqualTo(77L);
        assertThat(board.valid()).isTrue();
        assertThat(board.validation().valid()).isTrue();
        assertThat(board.roleCodes()).containsExactly("EMPATH", "CHEF", "MONK", "POISONER", "IMP");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Specification<ClocktowerBoardConfigPo>> specificationCaptor =
                ArgumentCaptor.forClass(Specification.class);
        verify(configRepository).findAll(specificationCaptor.capture(), eq(PageRequest.of(0, 20)));
        assertBoardSpecificationFiltersByOwnerAndQuery(specificationCaptor.getValue());
    }

    @Test
    void deleteRejectsBoardOwnedByAnotherUser() {
        ClocktowerBoardConfigRepository configRepository = mock(ClocktowerBoardConfigRepository.class);
        ClocktowerBoardRoleRepository roleRepository = mock(ClocktowerBoardRoleRepository.class);
        ClocktowerBoardConfigPo config = boardConfig(88L, "CTB-OTHER", ClocktowerScriptCode.TROUBLE_BREWING,
                5, 2L, true);
        when(configRepository.findByIdAndDeletedFalse(88L)).thenReturn(Optional.of(config));
        ClocktowerBoardService service = new ClocktowerBoardServiceImpl(ClocktowerBoardTestFactory.roleMetadataProvider(),
                ClocktowerBoardTestFactory.ruleEngine(), configRepository, roleRepository, new ObjectMapper());

        assertThatThrownBy(() -> service.delete(88L, principal(1L)))
                .isInstanceOf(ClocktowerException.class)
                .hasMessage("CLOCKTOWER_BOARD_FORBIDDEN");
        assertThat(config.isDeleted()).isFalse();
    }

    @Test
    void usableBoardRejectsInvalidOwnedBoard() {
        ClocktowerBoardConfigRepository configRepository = mock(ClocktowerBoardConfigRepository.class);
        ClocktowerBoardRoleRepository roleRepository = mock(ClocktowerBoardRoleRepository.class);
        ClocktowerBoardConfigPo config = boardConfig(89L, "CTB-INVALID", ClocktowerScriptCode.TROUBLE_BREWING,
                5, 1L, false);
        when(configRepository.findByIdAndDeletedFalse(89L)).thenReturn(Optional.of(config));
        ClocktowerBoardService service = new ClocktowerBoardServiceImpl(ClocktowerBoardTestFactory.roleMetadataProvider(),
                ClocktowerBoardTestFactory.ruleEngine(), configRepository, roleRepository, new ObjectMapper());

        assertThatThrownBy(() -> service.usableBoard(89L, null, principal(1L)))
                .isInstanceOf(ClocktowerException.class)
                .hasMessage("CLOCKTOWER_BOARD_INVALID");
    }

    @Test
    void usableBoardRejectsBoardOwnedByAnotherUser() {
        ClocktowerBoardConfigRepository configRepository = mock(ClocktowerBoardConfigRepository.class);
        ClocktowerBoardRoleRepository roleRepository = mock(ClocktowerBoardRoleRepository.class);
        ClocktowerBoardConfigPo config = boardConfig(90L, "CTB-OTHER", ClocktowerScriptCode.TROUBLE_BREWING,
                5, 2L, true);
        when(configRepository.findByBoardCodeAndDeletedFalse("CTB-OTHER")).thenReturn(Optional.of(config));
        ClocktowerBoardService service = new ClocktowerBoardServiceImpl(ClocktowerBoardTestFactory.roleMetadataProvider(),
                ClocktowerBoardTestFactory.ruleEngine(), configRepository, roleRepository, new ObjectMapper());

        assertThatThrownBy(() -> service.usableBoard(null, "CTB-OTHER", principal(1L)))
                .isInstanceOf(ClocktowerException.class)
                .hasMessage("CLOCKTOWER_BOARD_FORBIDDEN");
    }

    @Test
    void usableBoardRejectsAnonymousBeforeNullOwnedBoardCanMatch() {
        ClocktowerBoardConfigRepository configRepository = mock(ClocktowerBoardConfigRepository.class);
        ClocktowerBoardRoleRepository roleRepository = mock(ClocktowerBoardRoleRepository.class);
        ClocktowerBoardConfigPo config = boardConfig(91L, "CTB-ANON", ClocktowerScriptCode.TROUBLE_BREWING,
                5, null, true);
        when(configRepository.findByIdAndDeletedFalse(91L)).thenReturn(Optional.of(config));
        ClocktowerBoardService service = new ClocktowerBoardServiceImpl(ClocktowerBoardTestFactory.roleMetadataProvider(),
                ClocktowerBoardTestFactory.ruleEngine(), configRepository, roleRepository, new ObjectMapper());

        assertThatThrownBy(() -> service.usableBoard(91L, null, null))
                .isInstanceOf(ClocktowerException.class)
                .hasMessage("CLOCKTOWER_AUTH_REQUIRED");
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

    private static ClocktowerBoardConfigPo boardConfig(Long id, String boardCode, ClocktowerScriptCode scriptCode,
                                                       int playerCount, Long createdBy, boolean valid) {
        ClocktowerBoardConfigPo config = new ClocktowerBoardConfigPo();
        config.setId(id);
        config.setBoardCode(boardCode);
        config.setScriptCode(scriptCode);
        config.setPlayerCount(playerCount);
        config.setCreatedBy(createdBy);
        config.setValid(valid);
        config.setCreatedAt(java.time.Instant.parse("2026-06-19T03:00:00Z"));
        config.setValidationJson("""
                {"valid":%s,"roleTypeCounts":{"TOWNSFOLK":3,"MINION":1,"DEMON":1},"violations":[],"scores":[]}
                """.formatted(valid));
        return config;
    }

    private static ClocktowerBoardRolePo boardRole(String roleCode, int sortOrder) {
        ClocktowerBoardRolePo role = new ClocktowerBoardRolePo();
        role.setRoleCode(roleCode);
        role.setRoleType(ClocktowerRoleType.TOWNSFOLK);
        role.setSortOrder(sortOrder);
        return role;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void assertBoardSpecificationFiltersByOwnerAndQuery(
            Specification<ClocktowerBoardConfigPo> specification) {
        Root<ClocktowerBoardConfigPo> root = mock(Root.class);
        CriteriaQuery<?> criteriaQuery = mock(CriteriaQuery.class);
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        Path<Boolean> deletedPath = mock(Path.class);
        Path<Long> createdByPath = mock(Path.class);
        Path<ClocktowerScriptCode> scriptCodePath = mock(Path.class);
        Path<Integer> playerCountPath = mock(Path.class);
        Path<Boolean> validPath = mock(Path.class);
        Predicate predicate = mock(Predicate.class);
        when(root.get("deleted")).thenReturn((Path) deletedPath);
        when(root.get("createdBy")).thenReturn((Path) createdByPath);
        when(root.get("scriptCode")).thenReturn((Path) scriptCodePath);
        when(root.get("playerCount")).thenReturn((Path) playerCountPath);
        when(root.get("valid")).thenReturn((Path) validPath);
        when(criteriaBuilder.isFalse(deletedPath)).thenReturn(predicate);
        when(criteriaBuilder.equal(createdByPath, 1L)).thenReturn(predicate);
        when(criteriaBuilder.equal(scriptCodePath, ClocktowerScriptCode.TROUBLE_BREWING)).thenReturn(predicate);
        when(criteriaBuilder.equal(playerCountPath, 5)).thenReturn(predicate);
        when(criteriaBuilder.equal(validPath, true)).thenReturn(predicate);
        when(criteriaBuilder.and(any(Predicate[].class))).thenReturn(predicate);

        assertThat(specification.toPredicate(root, criteriaQuery, criteriaBuilder)).isSameAs(predicate);

        verify(criteriaBuilder).isFalse(deletedPath);
        verify(criteriaBuilder).equal(createdByPath, 1L);
        verify(criteriaBuilder).equal(scriptCodePath, ClocktowerScriptCode.TROUBLE_BREWING);
        verify(criteriaBuilder).equal(playerCountPath, 5);
        verify(criteriaBuilder).equal(validPath, true);
    }

    private static RbacPrincipal principal(Long userId) {
        return new RbacPrincipal(userId, "user-" + userId, Set.of("CLOCKTOWER_STORYTELLER"), Set.of(), "v1");
    }
}
