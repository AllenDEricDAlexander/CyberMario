package top.egon.mario.rbac.dto.enums;

import top.egon.mario.common.enums.CodedEnum;

import java.util.Map;
import java.util.Objects;

/**
 * JSON helper for API-layer coded enums.
 */
final class EnumJsonSupport {

    private EnumJsonSupport() {
    }

    static Map<String, Object> toJson(CodedEnum value) {
        return Map.of("code", value.getCode(), "desc", value.getDesc());
    }

    static <E extends Enum<E> & CodedEnum> E fromJson(Class<E> enumType, Object input) {
        if (input == null) {
            return null;
        }
        if (input instanceof Number number) {
            return CodedEnum.fromCode(enumType, number.intValue());
        }
        if (input instanceof Map<?, ?> map && map.get("code") instanceof Number number) {
            return CodedEnum.fromCode(enumType, number.intValue());
        }
        String text = String.valueOf(input).trim();
        for (E value : enumType.getEnumConstants()) {
            if (Objects.equals(value.name(), text) || Objects.equals(value.getDesc(), text)
                    || Objects.equals(String.valueOf(value.getCode()), text)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown " + enumType.getSimpleName() + " value: " + input);
    }

}
