package top.egon.mario.clocktower.admin.dto;

import top.egon.mario.clocktower.chat.dto.ClocktowerChatConversationResponse;
import top.egon.mario.clocktower.game.po.ClocktowerRoomProfilePo;
import top.egon.mario.clocktower.game.po.ClocktowerRoomSeatPo;
import top.egon.mario.clocktower.replay.dto.ClocktowerGameHistoryResponse;
import top.egon.mario.room.po.RoomBanPo;
import top.egon.mario.room.po.RoomInvitationPo;
import top.egon.mario.room.po.RoomMemberPo;
import top.egon.mario.room.po.RoomSpacePo;

import java.time.Instant;
import java.util.List;

public record ClocktowerRoomAuditResponse(
        Long roomId,
        String roomCode,
        String name,
        String roomStatus,
        String profileStatus,
        String visibility,
        Long storytellerUserId,
        int playerCount,
        Long currentGameId,
        List<Seat> seats,
        List<Member> members,
        List<Invitation> invitations,
        List<Ban> bans,
        List<ClocktowerGameHistoryResponse> games,
        List<ClocktowerChatConversationResponse> conversations
) {

    public static ClocktowerRoomAuditResponse from(RoomSpacePo room, ClocktowerRoomProfilePo profile,
                                                   List<Seat> seats, List<Member> members,
                                                   List<Invitation> invitations, List<Ban> bans,
                                                   List<ClocktowerGameHistoryResponse> games,
                                                   List<ClocktowerChatConversationResponse> conversations) {
        return new ClocktowerRoomAuditResponse(room.getId(), room.getRoomCode(), room.getName(), room.getStatus(),
                profile.getStatus(), room.getVisibility(), profile.getStorytellerUserId(),
                profile.getPlayerCount(), profile.getCurrentGameId(), seats, members, invitations, bans,
                games, conversations);
    }

    public record Seat(
            Long seatId,
            int seatNo,
            Long roomMemberId,
            Long userId,
            String displayName,
            String roleCode,
            String status,
            boolean traveler
    ) {

        public static Seat from(ClocktowerRoomSeatPo seat) {
            return new Seat(seat.getId(), seat.getSeatNo(), seat.getRoomMemberId(), seat.getUserId(),
                    seat.getDisplayName(), seat.getRoleCode(), seat.getStatus(), seat.isTraveler());
        }
    }

    public record Member(
            Long memberId,
            Long userId,
            String memberType,
            String status,
            Boolean activeStatus,
            Integer seatNo,
            String displayName,
            Instant joinedAt,
            Instant leftAt
    ) {

        public static Member from(RoomMemberPo member) {
            return new Member(member.getId(), member.getUserId(), member.getMemberType(), member.getStatus(),
                    member.getActiveStatus(), member.getSeatNo(), member.getDisplayName(), member.getJoinedAt(),
                    member.getLeftAt());
        }
    }

    public record Invitation(
            Long invitationId,
            Long inviterUserId,
            Long inviteeUserId,
            String status,
            Boolean activeStatus,
            Integer targetSeatNo,
            Instant expiresAt,
            Instant acceptedAt
    ) {

        public static Invitation from(RoomInvitationPo invitation) {
            return new Invitation(invitation.getId(), invitation.getInviterUserId(), invitation.getInviteeUserId(),
                    invitation.getStatus(), invitation.getActiveStatus(), invitation.getTargetSeatNo(),
                    invitation.getExpiresAt(), invitation.getAcceptedAt());
        }
    }

    public record Ban(
            Long banId,
            Long userId,
            Long bannedByUserId,
            String reason,
            String status,
            Instant expiresAt
    ) {

        public static Ban from(RoomBanPo ban) {
            return new Ban(ban.getId(), ban.getUserId(), ban.getBannedByUserId(), ban.getReason(), ban.getStatus(),
                    ban.getExpiresAt());
        }
    }
}
