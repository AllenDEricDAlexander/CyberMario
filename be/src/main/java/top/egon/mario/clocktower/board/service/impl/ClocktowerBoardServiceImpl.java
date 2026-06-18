package top.egon.mario.clocktower.board.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardGenerateRequest;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardSaveRequest;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardValidateRequest;
import top.egon.mario.clocktower.board.dto.response.BoardValidationResponse;
import top.egon.mario.clocktower.board.dto.response.ClocktowerBoardCandidateResponse;
import top.egon.mario.clocktower.board.dto.response.ClocktowerBoardConfigResponse;
import top.egon.mario.clocktower.board.dto.response.ClocktowerBoardGenerateResponse;
import top.egon.mario.clocktower.board.dto.response.ClocktowerBoardValidationResponse;
import top.egon.mario.clocktower.board.dto.response.ClocktowerRoleTypeCountResponse;
import top.egon.mario.clocktower.board.dto.response.ClocktowerRuleViolationResponse;
import top.egon.mario.clocktower.board.dto.response.ClocktowerScoreResponse;
import top.egon.mario.clocktower.board.po.ClocktowerBoardConfigPo;
import top.egon.mario.clocktower.board.po.ClocktowerBoardRolePo;
import top.egon.mario.clocktower.board.repository.ClocktowerBoardConfigRepository;
import top.egon.mario.clocktower.board.repository.ClocktowerBoardRoleRepository;
import top.egon.mario.clocktower.board.service.ClocktowerBoardService;
import top.egon.mario.clocktower.board.service.RoleMetadataProvider;
import top.egon.mario.clocktower.common.enums.ClocktowerRoleType;
import top.egon.mario.clocktower.engine.BoardCandidateFact;
import top.egon.mario.clocktower.engine.ClocktowerRuleEngine;
import top.egon.mario.clocktower.engine.RuleDecisionCollector;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class ClocktowerBoardServiceImpl implements ClocktowerBoardService {

    private final RoleMetadataProvider roleMetadataProvider;
    private final ClocktowerRuleEngine ruleEngine;
    private final ClocktowerBoardConfigRepository boardConfigRepository;
    private final ClocktowerBoardRoleRepository boardRoleRepository;
    private final ObjectMapper objectMapper;

    @Override
    public BoardValidationResponse validate(ClocktowerBoardValidateRequest request) {
        Map<String, ClocktowerRoleType> roleTypes = roleMetadataProvider.roleTypes(request.scriptCode());
        ClocktowerRoleTypeCountResponse typeCounts = countRoleTypes(request.roleCodes(), roleTypes);
        BoardCandidateFact fact = new BoardCandidateFact(request.scriptCode(), request.playerCount(), request.roleCodes(),
                typeCounts.townsfolk(), typeCounts.outsider(), typeCounts.minion(), typeCounts.demon());
        RuleDecisionCollector collector = ruleEngine.validateBoard(fact);
        List<ClocktowerRuleViolationResponse> issues = collector.violations().stream()
                .map(ClocktowerRuleViolationResponse::from)
                .toList();
        List<ClocktowerScoreResponse> scores = collector.scores().stream()
                .map(ClocktowerScoreResponse::from)
                .toList();
        return new BoardValidationResponse(issues.isEmpty(), typeCounts, issues, scores);
    }

    @Override
    public ClocktowerBoardGenerateResponse generate(ClocktowerBoardGenerateRequest request, RbacPrincipal principal) {
        int candidateCount = Math.max(request.candidateCount(), 1);
        List<ClocktowerBoardCandidateResponse> candidates = IntStream.range(0, candidateCount)
                .mapToObj(index -> candidate(request, index))
                .toList();
        return new ClocktowerBoardGenerateResponse(candidates);
    }

    @Override
    @Transactional
    public ClocktowerBoardConfigResponse save(ClocktowerBoardSaveRequest request, RbacPrincipal principal) {
        ClocktowerBoardConfigPo config = new ClocktowerBoardConfigPo();
        config.setBoardCode("CTB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        config.setScriptCode(request.scriptCode());
        config.setPlayerCount(request.playerCount());
        config.setDifficulty(request.difficulty());
        config.setChaos(request.chaos());
        config.setEvilPressure(request.evilPressure());
        config.setNewbieFriendly(request.newbieFriendly());
        config.setSeed(request.seed());
        config.setValidationJson(writeJson(request.validation()));
        ClocktowerBoardConfigPo saved = boardConfigRepository.save(config);

        Map<String, ClocktowerRoleType> roleTypes = roleMetadataProvider.roleTypes(request.scriptCode());
        for (int i = 0; i < request.roleCodes().size(); i++) {
            ClocktowerBoardRolePo role = new ClocktowerBoardRolePo();
            role.setBoardConfigId(saved.getId());
            role.setRoleCode(request.roleCodes().get(i));
            role.setRoleType(roleTypes.getOrDefault(role.getRoleCode(), ClocktowerRoleType.TOWNSFOLK));
            role.setSortOrder(i + 1);
            boardRoleRepository.save(role);
        }
        return toResponse(saved, request.roleCodes(), request.validation());
    }

    @Override
    public List<ClocktowerBoardConfigResponse> list() {
        return boardConfigRepository.findByDeletedFalseOrderByIdDesc().stream()
                .map(config -> {
                    List<String> roleCodes = boardRoleRepository
                            .findByBoardConfigIdAndDeletedFalseOrderBySortOrderAsc(config.getId())
                            .stream()
                            .map(ClocktowerBoardRolePo::getRoleCode)
                            .toList();
                    return toResponse(config, roleCodes, readValidation(config.getValidationJson()));
                })
                .toList();
    }

    @Override
    @Transactional
    public void delete(Long boardId, RbacPrincipal principal) {
        boardConfigRepository.findByIdAndDeletedFalse(boardId).ifPresent(config -> {
            config.setDeleted(true);
            boardConfigRepository.save(config);
        });
    }

    private ClocktowerBoardCandidateResponse candidate(ClocktowerBoardGenerateRequest request, int index) {
        List<String> roles = candidateRoles(request, index);
        BoardValidationResponse validation = validate(new ClocktowerBoardValidateRequest(
                request.scriptCode(), request.playerCount(), roles));
        ClocktowerBoardValidationResponse boardValidation = ClocktowerBoardValidationResponse.from(
                validation, roleTypeCountMap(validation.typeCounts()));
        return new ClocktowerBoardCandidateResponse(
                "candidate-" + (index + 1),
                request.scriptCode(),
                request.playerCount(),
                roles,
                roleMetadataProvider.roleSummaries(request.scriptCode(), roles),
                boardValidation,
                validation.scores()
        );
    }

    private List<String> candidateRoles(ClocktowerBoardGenerateRequest request, int index) {
        Map<String, ClocktowerRoleType> roleTypes = roleMetadataProvider.roleTypes(request.scriptCode());
        Map<ClocktowerRoleType, Integer> shape = shape(request.playerCount());
        List<String> roles = new ArrayList<>();
        List<String> banned = request.bannedRoleCodes() == null ? List.of() : request.bannedRoleCodes();
        if (request.lockedRoleCodes() != null) {
            request.lockedRoleCodes().stream()
                    .filter(roleCode -> !banned.contains(roleCode))
                    .filter(roleTypes::containsKey)
                    .forEach(roles::add);
        }
        for (Map.Entry<ClocktowerRoleType, Integer> entry : shape.entrySet()) {
            List<String> available = roleMetadataProvider.roleCodes(request.scriptCode(), entry.getKey()).stream()
                    .filter(roleCode -> !banned.contains(roleCode))
                    .filter(roleCode -> !roles.contains(roleCode))
                    .toList();
            for (String roleCode : rotate(available, index)) {
                if (countType(roles, entry.getKey(), roleTypes) >= entry.getValue()) {
                    break;
                }
                roles.add(roleCode);
            }
        }
        return roles.stream().limit(request.playerCount()).toList();
    }

    private List<String> rotate(List<String> roleCodes, int offset) {
        if (roleCodes.isEmpty()) {
            return roleCodes;
        }
        int shift = Math.floorMod(offset, roleCodes.size());
        List<String> rotated = new ArrayList<>(roleCodes.size());
        rotated.addAll(roleCodes.subList(shift, roleCodes.size()));
        rotated.addAll(roleCodes.subList(0, shift));
        return rotated;
    }

    private Map<ClocktowerRoleType, Integer> shape(int playerCount) {
        Map<ClocktowerRoleType, Integer> shape = new EnumMap<>(ClocktowerRoleType.class);
        shape.put(ClocktowerRoleType.TOWNSFOLK, playerCount == 5 ? 3 : 3);
        shape.put(ClocktowerRoleType.OUTSIDER, playerCount == 6 ? 1 : Math.max(playerCount - 5, 0));
        shape.put(ClocktowerRoleType.MINION, 1);
        shape.put(ClocktowerRoleType.DEMON, 1);
        return shape;
    }

    private int countType(List<String> roleCodes, ClocktowerRoleType roleType,
                          Map<String, ClocktowerRoleType> roleTypes) {
        return (int) roleCodes.stream().filter(roleCode -> roleTypes.get(roleCode) == roleType).count();
    }

    private Map<String, Integer> roleTypeCountMap(ClocktowerRoleTypeCountResponse typeCounts) {
        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        counts.put(ClocktowerRoleType.TOWNSFOLK.name(), typeCounts.townsfolk());
        counts.put(ClocktowerRoleType.OUTSIDER.name(), typeCounts.outsider());
        counts.put(ClocktowerRoleType.MINION.name(), typeCounts.minion());
        counts.put(ClocktowerRoleType.DEMON.name(), typeCounts.demon());
        counts.put(ClocktowerRoleType.TRAVELER.name(), typeCounts.traveler());
        counts.put(ClocktowerRoleType.FABLED.name(), typeCounts.fabled());
        return counts;
    }

    private ClocktowerBoardConfigResponse toResponse(ClocktowerBoardConfigPo config, List<String> roleCodes,
                                                     ClocktowerBoardValidationResponse validation) {
        return new ClocktowerBoardConfigResponse(config.getId(), config.getBoardCode(), config.getScriptCode(),
                config.getPlayerCount(), roleCodes, roleMetadataProvider.roleSummaries(config.getScriptCode(), roleCodes),
                validation);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("CLOCKTOWER_BOARD_VALIDATION_JSON_INVALID", e);
        }
    }

    private ClocktowerBoardValidationResponse readValidation(String json) {
        try {
            ObjectReader reader = objectMapper.readerFor(ClocktowerBoardValidationResponse.class);
            return reader.readValue(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("CLOCKTOWER_BOARD_VALIDATION_JSON_INVALID", e);
        }
    }

    private ClocktowerRoleTypeCountResponse countRoleTypes(List<String> roleCodes, Map<String, ClocktowerRoleType> roleTypes) {
        int townsfolk = 0;
        int outsider = 0;
        int minion = 0;
        int demon = 0;
        int traveler = 0;
        int fabled = 0;
        for (String roleCode : roleCodes) {
            ClocktowerRoleType roleType = roleTypes.get(roleCode);
            if (roleType == ClocktowerRoleType.TOWNSFOLK) {
                townsfolk++;
            } else if (roleType == ClocktowerRoleType.OUTSIDER) {
                outsider++;
            } else if (roleType == ClocktowerRoleType.MINION) {
                minion++;
            } else if (roleType == ClocktowerRoleType.DEMON) {
                demon++;
            } else if (roleType == ClocktowerRoleType.TRAVELER) {
                traveler++;
            } else if (roleType == ClocktowerRoleType.FABLED) {
                fabled++;
            }
        }
        return new ClocktowerRoleTypeCountResponse(townsfolk, outsider, minion, demon, traveler, fabled);
    }
}
