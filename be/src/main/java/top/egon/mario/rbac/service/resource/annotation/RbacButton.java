package top.egon.mario.rbac.service.resource.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a BUTTON permission resource.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(RbacButtons.class)
public @interface RbacButton {

    String code();

    String name();

    String menu();

    String action();

    String[] apiCodes() default {};

    int sort() default 0;

    String styleHint() default "";

    String description() default "";

}
