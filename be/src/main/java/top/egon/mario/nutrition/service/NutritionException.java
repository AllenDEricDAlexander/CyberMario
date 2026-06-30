package top.egon.mario.nutrition.service;

/**
 * Business exception raised by nutrition services.
 */
public class NutritionException extends RuntimeException {

    private final String code;

    public NutritionException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
