package top.egon.mario.rbac.service.resource.model;

/**
 * Menu-specific declaration carried by a MENU resource seed.
 */
public record RbacMenuSeed(
        String routeName,
        String routePath,
        String component,
        String redirect,
        String icon,
        boolean hidden,
        boolean cacheable,
        String externalLink
) {
}
