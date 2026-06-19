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
import top.egon.mario.clocktower.common.enums.ClocktowerAlignment;
import top.egon.mario.clocktower.common.enums.ClocktowerRoleType;
import top.egon.mario.clocktower.engine.BoardCandidateFact;
import top.egon.mario.clocktower.engine.ClocktowerRuleEngine;
import top.egon.mario.clocktower.engine.RuleDecisionCollector;
import top.egon.mario.clocktower.script.dto.response.ClocktowerRoleSummaryResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToIntFunction;
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
        List<String> roleCodes = normalizeRoleCodes(request.roleCodes());
        List<ClocktowerRoleSummaryResponse> scriptRoles = roleMetadataProvider.roles(request.scriptCode());
        Map<String, ClocktowerRoleType> roleTypes = roleTypes(scriptRoles);
        List<ClocktowerRuleViolationResponse> inputIssues = boardInputIssues(request, roleCodes, scriptRoles);
        ClocktowerRoleTypeCountResponse typeCounts = countRoleTypes(roleCodes, roleTypes);
        BoardCandidateFact fact = new BoardCandidateFact(request.scriptCode(), request.playerCount(), roleCodes,
                typeCounts.townsfolk(), typeCounts.outsider(), typeCounts.minion(), typeCounts.demon());
        RuleDecisionCollector collector = ruleEngine.validateBoard(fact);
        List<ClocktowerRuleViolationResponse> ruleIssues = collector.violations().stream()
                .map(ClocktowerRuleViolationResponse::from)
                .toList();
        List<ClocktowerRuleViolationResponse> issues = mergeIssues(inputIssues, ruleIssues);
        List<ClocktowerScoreResponse> scores = collector.scores().stream()
                .map(ClocktowerScoreResponse::from)
                .toList();
        boolean valid = issues.stream().noneMatch(issue -> "ERROR".equalsIgnoreCase(issue.severity()));
        return new BoardValidationResponse(valid, typeCounts, issues, scores);
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
        List<String> roleCodes = normalizeRoleCodes(request.roleCodes());
        BoardValidationResponse validation = validate(new ClocktowerBoardValidateRequest(
                request.scriptCode(), request.playerCount(), roleCodes));
        ClocktowerBoardValidationResponse boardValidation = ClocktowerBoardValidationResponse.from(
                validation, roleTypeCountMap(validation.typeCounts()));
        ClocktowerBoardConfigPo config = new ClocktowerBoardConfigPo();
        config.setBoardCode("CTB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        config.setScriptCode(request.scriptCode());
        config.setPlayerCount(request.playerCount());
        config.setValid(boardValidation.valid());
        config.setDifficulty(request.difficulty());
        config.setChaos(request.chaos());
        config.setEvilPressure(request.evilPressure());
        config.setNewbieFriendly(request.newbieFriendly());
        config.setSeed(request.seed());
        config.setValidationJson(writeJson(boardValidation));
        ClocktowerBoardConfigPo saved = boardConfigRepository.save(config);

        Map<String, ClocktowerRoleType> roleTypes = roleMetadataProvider.roleTypes(request.scriptCode());
        for (int i = 0; i < roleCodes.size(); i++) {
            ClocktowerBoardRolePo role = new ClocktowerBoardRolePo();
            role.setBoardConfigId(saved.getId());
            role.setRoleCode(roleCodes.get(i));
            role.setRoleType(roleTypes.getOrDefault(role.getRoleCode(), ClocktowerRoleType.TOWNSFOLK));
            role.setSortOrder(i + 1);
            boardRoleRepository.save(role);
        }
        return toResponse(saved, roleCodes, boardValidation);
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
        List<ClocktowerRoleSummaryResponse> roleSummaries = roleMetadataProvider.roleSummaries(request.scriptCode(), roles);
        BoardValidationResponse validation = validate(new ClocktowerBoardValidateRequest(
                request.scriptCode(), request.playerCount(), roles));
        ClocktowerBoardValidationResponse boardValidation = ClocktowerBoardValidationResponse.from(
                validation, roleTypeCountMap(validation.typeCounts()));
        List<ClocktowerScoreResponse> scores = new ArrayList<>(validation.scores());
        scores.addAll(preferenceScores(request, roleSummaries));
        return new ClocktowerBoardCandidateResponse(
                "candidate-" + (index + 1),
                request.scriptCode(),
                request.playerCount(),
                roles,
                roleSummaries,
                boardValidation,
                scores
        );
    }

    private List<String> candidateRoles(ClocktowerBoardGenerateRequest request, int index) {
        List<ClocktowerRoleSummaryResponse> roleSummaries = roleMetadataProvider.roles(request.scriptCode());
        Map<String, ClocktowerRoleType> roleTypes = roleTypes(roleSummaries);
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
            int requiredCount = entry.getValue() - countType(roles, entry.getKey(), roleTypes);
            if (requiredCount <= 0) {
                continue;
            }
            List<String> available = roleSummaries.stream()
                    .filter(role -> role.roleType() == entry.getKey())
                    .filter(role -> !banned.contains(role.roleCode()))
                    .filter(role -> !roles.contains(role.roleCode()))
                    .sorted(preferenceComparator(request))
                    .map(ClocktowerRoleSummaryResponse::roleCode)
                    .toList();
            for (String roleCode : diversify(available, requiredCount, index)) {
                roles.add(roleCode);
            }
        }
        return roles.stream().limit(request.playerCount()).toList();
    }

    private Map<String, ClocktowerRoleType> roleTypes(List<ClocktowerRoleSummaryResponse> roleSummaries) {
        Map<String, ClocktowerRoleType> roleTypes = new HashMap<>();
        for (ClocktowerRoleSummaryResponse role : roleSummaries) {
            roleTypes.putIfAbsent(role.roleCode(), role.roleType());
        }
        return roleTypes;
    }

    private Comparator<ClocktowerRoleSummaryResponse> preferenceComparator(ClocktowerBoardGenerateRequest request) {
        return Comparator
                .comparingInt((ClocktowerRoleSummaryResponse role) -> preferenceDistance(role, request))
                .thenComparingInt(role -> stableRank(request.seed(), role.roleCode()))
                .thenComparing(ClocktowerRoleSummaryResponse::roleCode);
    }

    private int preferenceDistance(ClocktowerRoleSummaryResponse role, ClocktowerBoardGenerateRequest request) {
        int difficultyDistance = Math.abs(difficultyLevel(role) - preferenceTarget(request.difficulty()));
        int chaosDistance = Math.abs(chaosLevel(role) - preferenceTarget(request.chaos()));
        int distance = difficultyDistance * 20 + chaosDistance * 14;
        if (role.alignment() == ClocktowerAlignment.EVIL
                || role.roleType() == ClocktowerRoleType.MINION || role.roleType() == ClocktowerRoleType.DEMON) {
            distance += Math.abs(evilPressureLevel(role) - preferenceTarget(request.evilPressure())) * 18;
        }
        if (request.newbieFriendly()) {
            distance += difficultyLevel(role) * 8 + chaosLevel(role) * 4 + (role.setupModifier() ? 8 : 0);
        }
        return distance;
    }

    private int stableRank(String seed, String roleCode) {
        return Math.floorMod(Objects.hash(seed == null ? "" : seed, roleCode), 10_000);
    }

    private List<String> diversify(List<String> roleCodes, int count, int index) {
        if (roleCodes.size() <= count) {
            return roleCodes;
        }
        int windowSize = Math.min(roleCodes.size(), count + Math.max(index, 1));
        int shift = Math.floorMod(index, windowSize);
        List<String> window = new ArrayList<>(windowSize);
        window.addAll(roleCodes.subList(shift, windowSize));
        window.addAll(roleCodes.subList(0, shift));
        return window.stream().limit(count).toList();
    }

    private Map<ClocktowerRoleType, Integer> shape(int playerCount) {
        return switch (playerCount) {
            case 5 -> shape(3, 0, 1, 1);
            case 6 -> shape(3, 1, 1, 1);
            case 7 -> shape(5, 0, 1, 1);
            case 8 -> shape(5, 1, 1, 1);
            case 9 -> shape(5, 2, 1, 1);
            case 10 -> shape(7, 0, 2, 1);
            case 11 -> shape(7, 1, 2, 1);
            case 12 -> shape(7, 2, 2, 1);
            case 13 -> shape(9, 0, 3, 1);
            case 14 -> shape(9, 1, 3, 1);
            case 15 -> shape(9, 2, 3, 1);
            default -> shape(3, Math.max(playerCount - 5, 0), 1, 1);
        };
    }

    private Map<ClocktowerRoleType, Integer> shape(int townsfolk, int outsider, int minion, int demon) {
        Map<ClocktowerRoleType, Integer> shape = new EnumMap<>(ClocktowerRoleType.class);
        shape.put(ClocktowerRoleType.TOWNSFOLK, townsfolk);
        shape.put(ClocktowerRoleType.OUTSIDER, outsider);
        shape.put(ClocktowerRoleType.MINION, minion);
        shape.put(ClocktowerRoleType.DEMON, demon);
        return shape;
    }

    private List<ClocktowerScoreResponse> preferenceScores(ClocktowerBoardGenerateRequest request,
                                                           List<ClocktowerRoleSummaryResponse> roles) {
        return List.of(
                preferenceScore("difficulty", request.difficulty(), averageLevel(roles, this::difficultyLevel),
                        "难度目标与候选角色复杂度的匹配度。"),
                preferenceScore("chaos", request.chaos(), averageLevel(roles, this::chaosLevel),
                        "混乱度目标与醉酒、中毒、误导、变形等能力的匹配度。"),
                preferenceScore("evilPressure", request.evilPressure(), averageLevel(roles.stream()
                                .filter(role -> role.alignment() == ClocktowerAlignment.EVIL
                                        || role.roleType() == ClocktowerRoleType.MINION
                                        || role.roleType() == ClocktowerRoleType.DEMON)
                                .toList(), this::evilPressureLevel),
                        "邪恶压力目标与爪牙、恶魔压迫能力的匹配度。")
        );
    }

    private ClocktowerScoreResponse preferenceScore(String scoreType, int target, double actual, String reason) {
        int distance = (int) Math.round(Math.abs(preferenceTarget(target) - actual));
        int delta = Math.max(0, 100 - distance * 25);
        return new ClocktowerScoreResponse(scoreType, delta,
                reason + " 目标 " + preferenceTarget(target) + "，候选均值 " + Math.round(actual) + "。");
    }

    private double averageLevel(List<ClocktowerRoleSummaryResponse> roles,
                                ToIntFunction<ClocktowerRoleSummaryResponse> scorer) {
        if (roles.isEmpty()) {
            return 1;
        }
        return roles.stream().mapToInt(scorer).average().orElse(1);
    }

    private int difficultyLevel(ClocktowerRoleSummaryResponse role) {
        int level = Math.max(role.complexity(), 1);
        if (role.firstNight()) {
            level++;
        }
        if (role.otherNight()) {
            level++;
        }
        if (role.setupModifier()) {
            level++;
        }
        String abilityText = abilityText(role);
        if (abilityText.length() > 50) {
            level++;
        }
        if (containsAny(abilityText, "如果", "可能", "以为", "当作", "交换", "变成", "醉酒", "中毒", "疯狂")) {
            level++;
        }
        return clampLevel(level);
    }

    private int chaosLevel(ClocktowerRoleSummaryResponse role) {
        int level = 1;
        String abilityText = abilityText(role);
        if (role.setupModifier()) {
            level++;
        }
        if (containsAny(abilityText, "醉酒", "中毒")) {
            level++;
        }
        if (containsAny(abilityText, "错误", "以为", "不知道", "当作")) {
            level++;
        }
        if (containsAny(abilityText, "交换", "变成", "疯狂", "额外")) {
            level++;
        }
        return clampLevel(level);
    }

    private int evilPressureLevel(ClocktowerRoleSummaryResponse role) {
        int level = role.roleType() == ClocktowerRoleType.DEMON ? 4 : 1;
        if (role.roleType() == ClocktowerRoleType.MINION) {
            level = 3;
        }
        String abilityText = abilityText(role);
        if (containsAny(abilityText, "死亡", "杀死")) {
            level++;
        }
        if (containsAny(abilityText, "中毒", "醉酒")) {
            level++;
        }
        if (containsAny(abilityText, "邪恶", "恶魔", "爪牙", "当作", "额外", "变成")) {
            level++;
        }
        if (role.setupModifier()) {
            level++;
        }
        return clampLevel(level);
    }

    private int preferenceTarget(int value) {
        return clampLevel(value);
    }

    private int clampLevel(int value) {
        return Math.max(1, Math.min(value, 5));
    }

    private String abilityText(ClocktowerRoleSummaryResponse role) {
        return role.abilityText() == null ? "" : role.abilityText();
    }

    private boolean containsAny(String text, String... tokens) {
        for (String token : tokens) {
            if (text.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private int countType(List<String> roleCodes, ClocktowerRoleType roleType,
                          Map<String, ClocktowerRoleType> roleTypes) {
        return (int) roleCodes.stream().filter(roleCode -> roleTypes.get(roleCode) == roleType).count();
    }

    private List<String> normalizeRoleCodes(Collection<String> roleCodes) {
        if (roleCodes == null) {
            return List.of();
        }
        return roleCodes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(roleCode -> !roleCode.isBlank())
                .toList();
    }

    private List<ClocktowerRuleViolationResponse> boardInputIssues(ClocktowerBoardValidateRequest request,
                                                                   List<String> roleCodes,
                                                                   List<ClocktowerRoleSummaryResponse> scriptRoles) {
        List<ClocktowerRuleViolationResponse> issues = new ArrayList<>();
        if (roleCodes.size() != request.playerCount()) {
            issues.add(new ClocktowerRuleViolationResponse("BOARD_ROLE_COUNT_MISMATCH",
                    "角色数量必须和玩家人数一致。", "ERROR"));
        }
        Set<String> scriptRoleCodes = scriptRoles.stream()
                .map(ClocktowerRoleSummaryResponse::roleCode)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Map<String, ClocktowerRoleSummaryResponse> enabledRoles = roleMetadataProvider.enabledRoles(roleCodes).stream()
                .collect(java.util.stream.Collectors.toMap(ClocktowerRoleSummaryResponse::roleCode,
                        java.util.function.Function.identity(), (left, right) -> left, LinkedHashMap::new));
        for (String roleCode : roleCodes) {
            ClocktowerRoleSummaryResponse enabledRole = enabledRoles.get(roleCode);
            if (enabledRole == null) {
                issues.add(new ClocktowerRuleViolationResponse("BOARD_ROLE_NOT_FOUND",
                        "角色不存在或未启用：" + roleCode, "ERROR"));
            } else if (!scriptRoleCodes.contains(roleCode)) {
                issues.add(new ClocktowerRuleViolationResponse("BOARD_ROLE_SCRIPT_MISMATCH",
                        "角色不属于所选剧本：" + roleCode, "ERROR"));
            }
        }
        return issues;
    }

    private List<ClocktowerRuleViolationResponse> mergeIssues(List<ClocktowerRuleViolationResponse> inputIssues,
                                                              List<ClocktowerRuleViolationResponse> ruleIssues) {
        Map<String, ClocktowerRuleViolationResponse> issues = new LinkedHashMap<>();
        for (ClocktowerRuleViolationResponse issue : inputIssues) {
            issues.putIfAbsent(issue.code(), issue);
        }
        for (ClocktowerRuleViolationResponse issue : ruleIssues) {
            issues.putIfAbsent(issue.code(), issue);
        }
        return List.copyOf(issues.values());
    }

    private Map<String, Integer> roleTypeCountMap(ClocktowerRoleTypeCountResponse typeCounts) {
        Map<String, Integer> counts = new LinkedHashMap<>();
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
                config.getPlayerCount(), config.isValid(), config.getCreatedAt(), roleCodes,
                roleMetadataProvider.roleSummaries(config.getScriptCode(), roleCodes), validation);
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
