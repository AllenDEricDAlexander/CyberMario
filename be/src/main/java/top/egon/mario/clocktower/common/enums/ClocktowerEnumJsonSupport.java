package top.egon.mario.clocktower.common.enums;

import top.egon.mario.common.enums.CodedEnum;

import java.util.Map;
import java.util.Objects;

/**
 * JSON helper for Clocktower API-layer coded enums.
 */
final class ClocktowerEnumJsonSupport {

    private ClocktowerEnumJsonSupport() {
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
        if (input instanceof Map<?, ?> map) {
            Object code = map.get("code");
            if (code instanceof Number number) {
                return CodedEnum.fromCode(enumType, number.intValue());
            }
            Object name = map.get("name");
            if (name != null) {
                return fromText(enumType, String.valueOf(name), input);
            }
            Object desc = map.get("desc");
            if (desc != null) {
                return fromText(enumType, String.valueOf(desc), input);
            }
        }
        return fromText(enumType, String.valueOf(input), input);
    }

    private static <E extends Enum<E> & CodedEnum> E fromText(Class<E> enumType, String input, Object originalInput) {
        String text = input.trim();
        for (E value : enumType.getEnumConstants()) {
            if (Objects.equals(value.name(), text) || Objects.equals(value.getDesc(), text)
                    || Objects.equals(String.valueOf(value.getCode()), text)
                    || value.name().equalsIgnoreCase(text)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown " + enumType.getSimpleName() + " value: " + originalInput);
    }
}
