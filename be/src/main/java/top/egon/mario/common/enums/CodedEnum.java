package top.egon.mario.common.enums;

/**
 * Enum contract for values stored as integer codes and displayed by descriptions.
 */
public interface CodedEnum {

    int getCode();

    String getDesc();

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

}
