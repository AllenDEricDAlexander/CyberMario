package top.egon.mario.room.policy;

import top.egon.mario.room.context.RoomContext;

public interface RoomReadPolicy extends RoomTypedPolicy {

    boolean canList(RoomContext context);
}
