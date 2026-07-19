package top.egon.mario.im.cache;

import top.egon.mario.im.po.enums.ImSurfaceType;

/**
 * Immutable cache value that resolves a public join key to an internal surface identity.
 */
public record ImSurfaceJoinKeyRef(ImSurfaceType surfaceType, Long surfaceId) {
}
