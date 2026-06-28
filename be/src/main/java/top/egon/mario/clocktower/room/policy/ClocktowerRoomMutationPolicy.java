package top.egon.mario.clocktower.room.policy;

import org.springframework.stereotype.Component;
import top.egon.mario.room.context.RoomContext;
import top.egon.mario.room.policy.RoomMutation;
import top.egon.mario.room.policy.RoomMutationPolicy;

import java.util.Objects;

@Component
public class ClocktowerRoomMutationPolicy implements RoomMutationPolicy {

    public static final String CONTEXT_TYPE = "CLOCKTOWER_ROOM";

    @Override
    public String contextType() {
        return CONTEXT_TYPE;
    }

    @Override
    public boolean canMutate(RoomContext context, RoomMutation mutation) {
        if (context == null || mutation == null) {
            return false;
        }
        if (mutation == RoomMutation.CREATE_ROOM) {
            return context.ownerUserId() != null;
        }
        if (!"ACTIVE".equals(context.roomStatus())) {
            return false;
        }
        return switch (mutation) {
            case ENTER_ROOM, HEARTBEAT, ACCEPT_INVITATION, DECLINE_INVITATION -> context.viewerUserId() != null;
            case INVITE, KICK, BAN, REFRESH_RESERVATIONS -> isOwner(context);
            case CREATE_ROOM -> false;
        };
    }

    private boolean isOwner(RoomContext context) {
        return context.viewerUserId() != null
                && Objects.equals(context.viewerUserId(), context.ownerUserId());
    }
}
