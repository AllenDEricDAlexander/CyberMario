package top.egon.mario.rbac.service.resource.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a bean class as an RBAC resource declaration module.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RbacResourceModule {

    String appCode();

    String name();

    String[] codePrefixes() default {};

}
