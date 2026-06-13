package top.egon.mario.rbac.service.resource.model;

/**
 * Button-specific declaration carried by a BUTTON resource seed.
 */
public record RbacButtonSeed(
        String buttonKey,
        String frontendAction,
        String styleHint
) {
}
