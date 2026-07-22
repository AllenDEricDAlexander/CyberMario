package top.egon.mario.agent.externalim.guard;

import org.springframework.util.StringUtils;

import java.math.BigDecimal;

public record ChatGuardResult(
        ChatGuardDecision decision,
        BigDecimal confidence,
        String reason,
        String modelProvider,
        String modelName,
        long durationMs
) {

    public ChatGuardResult {
        decision = decision == null ? ChatGuardDecision.IGNORE : decision;
        confidence = confidence == null ? BigDecimal.ZERO : confidence;
        reason = StringUtils.hasText(reason) ? reason.trim() : "guard returned no reason";
        if (reason.length() > 1000) {
            reason = reason.substring(0, 1000);
        }
        durationMs = Math.max(0L, durationMs);
    }

    public static ChatGuardResult reply(String reason) {
        return new ChatGuardResult(ChatGuardDecision.REPLY, BigDecimal.ONE,
                reason, null, null, 0L);
    }

    public static ChatGuardResult ignore(String reason) {
        return new ChatGuardResult(ChatGuardDecision.IGNORE, BigDecimal.ZERO,
                reason, null, null, 0L);
    }
}
