package top.egon.mario.rbac.converter;

import org.mapstruct.Mapper;
import top.egon.mario.common.enums.CodedEnum;

/**
 * Maps enum values across API DTO and persistence layers.
 */
@Mapper(componentModel = "spring")
public interface RbacLayerEnumMapper {

    default top.egon.mario.rbac.dto.enums.RbacStatus toDto(top.egon.mario.rbac.po.enums.RbacStatus status) {
        return fromCode(top.egon.mario.rbac.dto.enums.RbacStatus.class, status);
    }

    default top.egon.mario.rbac.po.enums.RbacStatus toPo(top.egon.mario.rbac.dto.enums.RbacStatus status) {
        return fromCode(top.egon.mario.rbac.po.enums.RbacStatus.class, status);
    }

    default top.egon.mario.rbac.dto.enums.PermissionType toDto(top.egon.mario.rbac.po.enums.PermissionType type) {
        return fromCode(top.egon.mario.rbac.dto.enums.PermissionType.class, type);
    }

    default top.egon.mario.rbac.po.enums.PermissionType toPo(top.egon.mario.rbac.dto.enums.PermissionType type) {
        return fromCode(top.egon.mario.rbac.po.enums.PermissionType.class, type);
    }

    default top.egon.mario.rbac.dto.enums.PermissionStatus toDto(top.egon.mario.rbac.po.enums.PermissionStatus status) {
        return fromCode(top.egon.mario.rbac.dto.enums.PermissionStatus.class, status);
    }

    default top.egon.mario.rbac.po.enums.PermissionStatus toPo(top.egon.mario.rbac.dto.enums.PermissionStatus status) {
        return fromCode(top.egon.mario.rbac.po.enums.PermissionStatus.class, status);
    }

    default top.egon.mario.rbac.dto.enums.ApiMatcherType toDto(top.egon.mario.rbac.po.enums.ApiMatcherType matcherType) {
        return fromCode(top.egon.mario.rbac.dto.enums.ApiMatcherType.class, matcherType);
    }

    default top.egon.mario.rbac.po.enums.ApiMatcherType toPo(top.egon.mario.rbac.dto.enums.ApiMatcherType matcherType) {
        return fromCode(top.egon.mario.rbac.po.enums.ApiMatcherType.class, matcherType);
    }

    default top.egon.mario.rbac.dto.enums.ApiRiskLevel toDto(top.egon.mario.rbac.po.enums.ApiRiskLevel riskLevel) {
        return fromCode(top.egon.mario.rbac.dto.enums.ApiRiskLevel.class, riskLevel);
    }

    default top.egon.mario.rbac.po.enums.ApiRiskLevel toPo(top.egon.mario.rbac.dto.enums.ApiRiskLevel riskLevel) {
        return fromCode(top.egon.mario.rbac.po.enums.ApiRiskLevel.class, riskLevel);
    }

    default top.egon.mario.rbac.dto.enums.ButtonApiRelationType toDto(top.egon.mario.rbac.po.enums.ButtonApiRelationType type) {
        return fromCode(top.egon.mario.rbac.dto.enums.ButtonApiRelationType.class, type);
    }

    default top.egon.mario.rbac.po.enums.ButtonApiRelationType toPo(top.egon.mario.rbac.dto.enums.ButtonApiRelationType type) {
        return fromCode(top.egon.mario.rbac.po.enums.ButtonApiRelationType.class, type);
    }

    private static <E extends Enum<E> & CodedEnum> E fromCode(Class<E> enumType, CodedEnum value) {
        return value == null ? null : CodedEnum.fromCode(enumType, value.getCode());
    }

}
