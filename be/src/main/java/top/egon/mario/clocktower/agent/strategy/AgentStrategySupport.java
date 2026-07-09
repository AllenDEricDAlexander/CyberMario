package top.egon.mario.clocktower.agent.strategy;

import top.egon.mario.clocktower.agent.view.dto.AgentLegalIntentView;
import top.egon.mario.clocktower.agent.view.dto.AgentMemoryView;

import java.util.Collection;
import java.util.List;
import java.util.Map;

final class AgentStrategySupport {

    private AgentStrategySupport() {
    }

    static AgentLegalIntentView firstIntent(AgentDecisionContext context, String intentType) {
        return context.legalIntents().stream()
                .filter(intent -> intentType.equals(intent.intentType()))
                .findFirst()
                .orElse(null);
    }

    static boolean hasIntent(AgentDecisionContext context, String intentType) {
        return firstIntent(context, intentType) != null;
    }

    static List<Long> longList(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .map(AgentStrategySupport::longValue)
                    .filter(item -> item != null)
                    .toList();
        }
        return List.of();
    }

    static Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? null : Long.valueOf(value.toString());
    }

    static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    static int memoryScore(AgentDecisionContext context, Long targetGameSeatId, String memoryType) {
        return context.view().memories().stream()
                .filter(memory -> memoryType.equals(memory.memoryType()))
                .filter(memory -> targetGameSeatId.equals(memory.subjectGameSeatId()))
                .map(AgentMemoryView::content)
                .map(content -> content.get("score"))
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .mapToInt(Number::intValue)
                .max()
                .orElse(50);
    }

    static List<Long> evilDemonSeatIds(AgentDecisionContext context) {
        Object evilTeam = context.view().roleSpecificContext().get("evilTeam");
        if (!(evilTeam instanceof Collection<?> entries)) {
            return List.of();
        }
        return entries.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .filter(entry -> Boolean.TRUE.equals(entry.get("isDemon")) || "DEMON".equals(entry.get("roleType")))
                .map(entry -> longValue(entry.get("gameSeatId")))
                .filter(item -> item != null)
                .toList();
    }

    static List<Long> evilTeamSeatIds(AgentDecisionContext context) {
        Object evilTeam = context.view().roleSpecificContext().get("evilTeam");
        if (!(evilTeam instanceof Collection<?> entries)) {
            return List.of();
        }
        return entries.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(entry -> longValue(entry.get("gameSeatId")))
                .filter(item -> item != null)
                .toList();
    }
}
