package top.egon.mario.room.policy;

import top.egon.mario.room.context.RoomContext;

public interface RoomMutationPolicy extends RoomTypedPolicy {

    boolean canMutate(RoomContext context, RoomMutation mutation);
}
