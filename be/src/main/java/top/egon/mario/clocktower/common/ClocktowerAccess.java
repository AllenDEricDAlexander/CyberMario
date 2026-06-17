package top.egon.mario.clocktower.common;

import top.egon.mario.clocktower.room.po.ClocktowerRoomPo;
import top.egon.mario.clocktower.room.po.ClocktowerSeatPo;
import top.egon.mario.rbac.service.security.RbacPrincipal;

public final class ClocktowerAccess {

    private static final String SUPER_ADMIN_ROLE_CODE = "SUPER_ADMIN";

    private ClocktowerAccess() {
    }

    public static void requireAuthenticated(RbacPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new ClocktowerException("CLOCKTOWER_AUTH_REQUIRED");
        }
    }

    public static boolean isSuperAdmin(RbacPrincipal principal) {
        return principal != null && principal.roleCodes() != null
                && principal.roleCodes().contains(SUPER_ADMIN_ROLE_CODE);
    }

    public static boolean isStoryteller(ClocktowerRoomPo room, RbacPrincipal principal) {
        return isSuperAdmin(principal)
                || principal != null
                && principal.userId() != null
                && principal.userId().equals(room.getStorytellerUserId());
    }

    public static void requireStoryteller(ClocktowerRoomPo room, RbacPrincipal principal) {
        if (!isStoryteller(room, principal)) {
            throw new ClocktowerException("CLOCKTOWER_STORYTELLER_FORBIDDEN");
        }
    }

    public static void requireSeatOwnerOrStoryteller(ClocktowerRoomPo room, ClocktowerSeatPo seat,
                                                     RbacPrincipal principal) {
        if (isStoryteller(room, principal)) {
            return;
        }
        requireAuthenticated(principal);
        if (!principal.userId().equals(seat.getUserId())) {
            throw new ClocktowerException("CLOCKTOWER_SEAT_FORBIDDEN");
        }
    }
}
