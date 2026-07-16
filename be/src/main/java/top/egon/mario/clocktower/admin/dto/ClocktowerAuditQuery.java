package top.egon.mario.clocktower.admin.dto;

import org.springframework.util.StringUtils;
import top.egon.mario.clocktower.common.ClocktowerException;

import java.util.List;

/**
 * Shared filter for every Clocktower audit report.
 */
public record ClocktowerAuditQuery(
        List<Long> roomIds,
        List<Long> gameIds,
        List<Long> conversationIds,
        String roomName) {

    private static final int MAX_IDS_PER_FILTER = 50;

    public ClocktowerAuditQuery {
        roomIds = normalizeIds(roomIds);
        gameIds = normalizeIds(gameIds);
        conversationIds = normalizeIds(conversationIds);
        roomName = StringUtils.hasText(roomName) ? roomName.trim() : null;
    }

    private static List<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        if (ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new ClocktowerException("CLOCKTOWER_AUDIT_FILTER_INVALID");
        }
        List<Long> normalized = ids.stream().distinct().toList();
        if (normalized.size() > MAX_IDS_PER_FILTER) {
            throw new ClocktowerException("CLOCKTOWER_AUDIT_FILTER_TOO_LARGE");
        }
        return normalized;
    }
}
