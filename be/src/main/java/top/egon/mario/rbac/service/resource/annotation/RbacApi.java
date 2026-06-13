package top.egon.mario.rbac.service.resource.annotation;

import top.egon.mario.rbac.po.enums.ApiMatcherType;
import top.egon.mario.rbac.po.enums.ApiRiskLevel;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares an API permission resource.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(RbacApis.class)
public @interface RbacApi {

    String code();

    String name();

    String appCode() default "";

    String method() default "";

    String pattern() default "";

    ApiMatcherType matcher() default ApiMatcherType.EXACT;

    ApiRiskLevel risk() default ApiRiskLevel.LOW;

    boolean publicFlag() default false;

    String serviceTag() default "";

    int sort() default 0;

    String description() default "";

}
