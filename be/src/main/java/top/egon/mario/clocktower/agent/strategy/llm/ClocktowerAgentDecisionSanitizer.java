package top.egon.mario.clocktower.agent.strategy.llm;

import org.springframework.util.StringUtils;

import java.util.Locale;

public class ClocktowerAgentDecisionSanitizer {

    private final int maxSpeechChars;

    public ClocktowerAgentDecisionSanitizer(int maxSpeechChars) {
        this.maxSpeechChars = maxSpeechChars <= 0 ? 500 : maxSpeechChars;
    }

    public String sanitizeSpeech(String content, boolean grimoireIncluded) {
        String sanitized = sanitizeText(content, "LLM_SPEECH_EMPTY");
        if (sanitized.length() > maxSpeechChars) {
            throw new ClocktowerAgentLlmPolicyException("LLM_SPEECH_TOO_LONG");
        }
        rejectUnsafe(sanitized, grimoireIncluded);
        return sanitized;
    }

    public String sanitizeReasoning(String reasoning) {
        if (!StringUtils.hasText(reasoning)) {
            return "LLM selected a legal intent";
        }
        String sanitized = reasoning.trim();
        rejectUnsafe(sanitized, true);
        return sanitized.length() > 300 ? sanitized.substring(0, 300) : sanitized;
    }

    private String sanitizeText(String value, String emptyCode) {
        if (!StringUtils.hasText(value)) {
            throw new ClocktowerAgentLlmPolicyException(emptyCode);
        }
        return value.trim();
    }

    private void rejectUnsafe(String value, boolean grimoireIncluded) {
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("system prompt")
                || lower.contains("language model")
                || lower.contains("full json")
                || value.contains("系统提示")
                || value.contains("我是 AI")
                || value.contains("我是AI")
                || value.contains("完整 JSON")
                || value.contains("说书人宣布")
                || value.contains("裁定为")
                || (!grimoireIncluded && lower.contains("grimoire"))) {
            throw new ClocktowerAgentLlmPolicyException("LLM_UNSAFE_CONTENT");
        }
    }
}
