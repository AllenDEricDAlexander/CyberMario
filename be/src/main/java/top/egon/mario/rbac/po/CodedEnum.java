package top.egon.mario.rbac.po;

import java.util.Map;
import java.util.Objects;

/**
 * Enum contract for values stored as integer codes and displayed by descriptions.
 */
public interface CodedEnum {

    int getCode();

    String getDesc();

    static Map<String, Object> toJson(CodedEnum value) {
        return Map.of("code", value.getCode(), "desc", value.getDesc());
    }

    static <E extends Enum<E> & CodedEnum> E fromCode(Class<E> enumType, Integer code) {
        if (code == null) {
            return null;
        }
        for (E value : enumType.getEnumConstants()) {
            if (value.getCode() == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown " + enumType.getSimpleName() + " code: " + code);
    }

    static <E extends Enum<E> & CodedEnum> E fromJson(Class<E> enumType, Object input) {
        if (input == null) {
            return null;
        }
        if (input instanceof Number number) {
            return fromCode(enumType, number.intValue());
        }
        if (input instanceof Map<?, ?> map && map.get("code") instanceof Number number) {
            return fromCode(enumType, number.intValue());
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
