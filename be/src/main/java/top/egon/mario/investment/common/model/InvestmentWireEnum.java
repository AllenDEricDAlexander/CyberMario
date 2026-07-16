package top.egon.mario.investment.common.model;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * Keeps Investment enum JSON values identical to their persisted names.
 */
public interface InvestmentWireEnum {

    @JsonValue
    default String wireValue() {
        return ((Enum<?>) this).name();
    }

    static <E extends Enum<E> & InvestmentWireEnum> E fromWireValue(Class<E> enumType, Object input) {
        if (!(input instanceof String value)) {
            throw new IllegalArgumentException(enumType.getSimpleName() + " wire value must be a string");
        }
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException ex) {
            String expectedValues = Arrays.stream(enumType.getEnumConstants())
                    .map(Enum::name)
                    .toList()
                    .toString();
            throw new IllegalArgumentException(
                    "Unknown " + enumType.getSimpleName() + " wire value: " + value + "; expected " + expectedValues,
                    ex);
        }
    }
}
