package top.egon.mario.rbac.service.model;

import top.egon.mario.rbac.dto.enums.ActivationStatus;
import top.egon.mario.rbac.po.enums.RbacStatus;

/**
 * Optional filters for the admin user list.
 *
 * <p>The console filters users before paging, so a search has to run in the
 * database rather than over the current page. Every field is nullable and an
 * absent field means "no restriction".
 *
 * @param keyword          matched case-insensitively against account no, username,
 *                         nickname, email and mobile
 * @param status           enabled/disabled account state
 * @param activationStatus derived from {@code activated_at} being null
 */
public record UserQuery(String keyword, RbacStatus status, ActivationStatus activationStatus) {

    public static final UserQuery EMPTY = new UserQuery(null, null, null);

    public boolean hasKeyword() {
        return keyword != null && !keyword.isBlank();
    }

    /** Lower-cased and wrapped for a {@code LIKE} comparison. */
    public String keywordPattern() {
        return "%" + keyword.trim().toLowerCase() + "%";
    }
}
