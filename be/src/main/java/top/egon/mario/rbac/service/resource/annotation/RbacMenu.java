package top.egon.mario.rbac.service.resource.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a MENU permission resource.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(RbacMenus.class)
public @interface RbacMenu {

    String code();

    String name();

    String parent() default "";

    String path() default "";

    String routeName() default "";

    String component() default "";

    String redirect() default "";

    String icon() default "";

    int sort() default 0;

    boolean hidden() default false;

    boolean cacheable() default true;

    String externalLink() default "";

    String description() default "";

}
