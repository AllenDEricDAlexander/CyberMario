package top.egon.mario.clocktower.admin.dto;

import java.time.Instant;

public final class ClocktowerAuditReportResponse {

    private ClocktowerAuditReportResponse() {
    }

    public record Room(
            Long roomId,
            String roomCode,
            String roomName,
            String status,
            String visibility,
            Long ownerUserId,
            int capacity,
            int currentMemberCount,
            Instant lastActiveAt,
            Instant createdAt) {
    }

    public record Game(
            Long gameId,
            Long roomId,
            String roomName,
            int gameNo,
            String scriptCode,
            String status,
            String phase,
            int dayNo,
            int nightNo,
            Instant startedAt,
            Instant endedAt,
            Instant lastActiveAt,
            Instant createdAt) {
    }

    public record Event(
            Long eventId,
            Long roomId,
            String roomName,
            Long gameId,
            Long eventSeq,
            String eventType,
            String phase,
            int dayNo,
            int nightNo,
            Long actorGameSeatId,
            Long targetGameSeatId,
            String visibility,
            String status,
            Instant occurredAt,
            String payloadJson) {
    }

    public record Conversation(
            Long conversationId,
            Long roomId,
            String roomName,
            Long gameId,
            Long channelId,
            String channelKey,
            String channelName,
            Long groupId,
            String groupKey,
            String groupName,
            String conversationType,
            String status,
            Long messageSeq,
            Instant lastMessageAt,
            Instant lastActiveAt,
            Instant createdAt) {
    }

    public record Message(
            Long messageId,
            Long conversationId,
            Long roomId,
            String roomName,
            Long gameId,
            String channelKey,
            String groupKey,
            Long senderUserId,
            Long messageSeq,
            String messageType,
            String content,
            String status,
            Instant sentAt,
            Instant editedAt) {
    }

    public record Member(
            Long memberId,
            Long roomId,
            String roomName,
            Long userId,
            String displayName,
            String memberType,
            String status,
            Boolean activeStatus,
            Integer seatNo,
            Instant joinedAt,
            Instant leftAt,
            Instant lastActiveAt,
            Instant createdAt) {
    }

    public record Invitation(
            Long invitationId,
            Long roomId,
            String roomName,
            Long inviterUserId,
            Long inviteeUserId,
            String invitationCode,
            String status,
            Boolean activeStatus,
            Integer targetSeatNo,
            Instant expiresAt,
            Instant acceptedAt,
            Instant createdAt) {
    }

    public record Ban(
            Long banId,
            Long roomId,
            String roomName,
            Long userId,
            Long bannedByUserId,
            String reason,
            String status,
            Instant expiresAt,
            Instant createdAt) {
    }
}
