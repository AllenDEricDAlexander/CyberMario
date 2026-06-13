package top.egon.mario.rbac.service.resource;

import lombok.RequiredArgsConstructor;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.result.method.RequestMappingInfo;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;
import top.egon.mario.rbac.po.enums.PermissionStatus;
import top.egon.mario.rbac.service.resource.annotation.RbacApi;
import top.egon.mario.rbac.service.resource.annotation.RbacButton;
import top.egon.mario.rbac.service.resource.annotation.RbacMenu;
import top.egon.mario.rbac.service.resource.annotation.RbacResourceModule;
import top.egon.mario.rbac.service.resource.model.RbacApiSeed;
import top.egon.mario.rbac.service.resource.model.RbacButtonSeed;
import top.egon.mario.rbac.service.resource.model.RbacMenuSeed;
import top.egon.mario.rbac.service.resource.model.RbacResourceSeed;
import top.egon.mario.rbac.service.resource.model.RbacResourceSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Converts local RBAC resource annotations on Spring beans into resource seeds.
 */
@Component
@RequiredArgsConstructor
public class AnnotationRbacResourceProvider {

    private final ApplicationContext applicationContext;
    private final List<RequestMappingHandlerMapping> handlerMappings;

    /**
     * Returns one provider per annotated app module found in the Spring context.
     */
    public List<RbacResourceProvider> providers() {
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(RbacResourceModule.class);
        Map<String, List<RbacResourceSeed>> resourcesByAppCode = new LinkedHashMap<>();
        for (Object bean : beans.values()) {
            Class<?> targetClass = AopUtils.getTargetClass(bean);
            RbacResourceModule module = AnnotatedElementUtils.findMergedAnnotation(targetClass, RbacResourceModule.class);
            if (module == null) {
                continue;
            }
            List<RbacResourceSeed> resources = new ArrayList<>();
            for (RbacMenu menu : AnnotatedElementUtils.findMergedRepeatableAnnotations(targetClass, RbacMenu.class)) {
                resources.add(menuSeed(module, menu));
            }
            for (RbacButton button : AnnotatedElementUtils.findMergedRepeatableAnnotations(targetClass, RbacButton.class)) {
                resources.add(buttonSeed(module, button));
            }
            for (RbacApi api : AnnotatedElementUtils.findMergedRepeatableAnnotations(targetClass, RbacApi.class)) {
                resources.add(apiSeed(module, api));
            }
            if (!resources.isEmpty()) {
                resourcesByAppCode.computeIfAbsent(module.appCode(), ignored -> new ArrayList<>()).addAll(resources);
            }
        }
        addMethodLevelApis(resourcesByAppCode);
        return resourcesByAppCode.entrySet().stream()
                .map(entry -> new StaticRbacResourceProvider(entry.getKey(), entry.getValue()))
                .map(RbacResourceProvider.class::cast)
                .toList();
    }

    private RbacResourceSeed menuSeed(RbacResourceModule module, RbacMenu menu) {
        return RbacResourceSeed.menu(
                module.appCode(),
                module.appCode(),
                menu.code(),
                menu.name(),
                emptyToNull(menu.parent()),
                PermissionStatus.ENABLED,
                menu.sort(),
                emptyToNull(menu.description()),
                new RbacMenuSeed(
                        emptyToNull(menu.routeName()),
                        emptyToNull(menu.path()),
                        emptyToNull(menu.component()),
                        emptyToNull(menu.redirect()),
                        emptyToNull(menu.icon()),
                        menu.hidden(),
                        menu.cacheable(),
                        emptyToNull(menu.externalLink())
                ),
                RbacResourceSource.ANNOTATION
        );
    }

    private RbacResourceSeed buttonSeed(RbacResourceModule module, RbacButton button) {
        return RbacResourceSeed.button(
                module.appCode(),
                module.appCode(),
                button.code(),
                button.name(),
                button.menu(),
                PermissionStatus.ENABLED,
                button.sort(),
                emptyToNull(button.description()),
                new RbacButtonSeed(button.action(), button.action(), emptyToNull(button.styleHint())),
                List.of(button.apiCodes()),
                RbacResourceSource.ANNOTATION
        );
    }

    private RbacResourceSeed apiSeed(RbacResourceModule module, RbacApi api) {
        return RbacResourceSeed.api(
                module.appCode(),
                StringUtils.hasText(api.serviceTag()) ? api.serviceTag() : module.appCode(),
                api.code(),
                api.name(),
                PermissionStatus.ENABLED,
                api.sort(),
                emptyToNull(api.description()),
                new RbacApiSeed(api.method(), api.pattern(), api.matcher(), api.publicFlag(), api.risk()),
                RbacResourceSource.ANNOTATION
        );
    }

    private void addMethodLevelApis(Map<String, List<RbacResourceSeed>> resourcesByAppCode) {
        for (RequestMappingHandlerMapping handlerMapping : handlerMappings) {
            for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
                RbacApi api = AnnotatedElementUtils.findMergedAnnotation(entry.getValue().getMethod(), RbacApi.class);
                if (api == null) {
                    continue;
                }
                RbacResourceModule module = AnnotatedElementUtils.findMergedAnnotation(entry.getValue().getBeanType(), RbacResourceModule.class);
                String appCode = null;
                if (module == null && StringUtils.hasText(api.appCode())) {
                    appCode = api.appCode();
                } else if (module == null) {
                    module = findSingleModule(resourcesByAppCode).orElse(null);
                }
                if (module == null && !StringUtils.hasText(appCode)) {
                    continue;
                }
                String targetAppCode = StringUtils.hasText(appCode) ? appCode : module.appCode();
                resourcesByAppCode.computeIfAbsent(targetAppCode, ignored -> new ArrayList<>())
                        .add(apiSeed(targetAppCode, api, entry.getKey()));
            }
        }
    }

    private Optional<RbacResourceModule> findSingleModule(Map<String, List<RbacResourceSeed>> resourcesByAppCode) {
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(RbacResourceModule.class);
        if (beans.size() != 1) {
            return Optional.empty();
        }
        Object bean = beans.values().iterator().next();
        return Optional.ofNullable(AnnotatedElementUtils.findMergedAnnotation(AopUtils.getTargetClass(bean), RbacResourceModule.class));
    }

    private RbacResourceSeed apiSeed(RbacResourceModule module, RbacApi api, RequestMappingInfo mappingInfo) {
        return apiSeed(module.appCode(), api, mappingInfo);
    }

    private RbacResourceSeed apiSeed(String appCode, RbacApi api, RequestMappingInfo mappingInfo) {
        String method = StringUtils.hasText(api.method()) ? api.method() : resolveMethod(mappingInfo);
        String pattern = StringUtils.hasText(api.pattern()) ? api.pattern() : resolvePattern(mappingInfo);
        return RbacResourceSeed.api(
                appCode,
                StringUtils.hasText(api.serviceTag()) ? api.serviceTag() : appCode,
                api.code(),
                api.name(),
                PermissionStatus.ENABLED,
                api.sort(),
                emptyToNull(api.description()),
                new RbacApiSeed(method, pattern, api.matcher(), api.publicFlag(), api.risk()),
                RbacResourceSource.ANNOTATION
        );
    }

    private String resolveMethod(RequestMappingInfo mappingInfo) {
        return mappingInfo.getMethodsCondition().getMethods().stream()
                .findFirst()
                .map(RequestMethod::name)
                .orElse("ANY");
    }

    private String resolvePattern(RequestMappingInfo mappingInfo) {
        return mappingInfo.getPatternsCondition().getPatterns().stream()
                .findFirst()
                .map(PathPattern::getPatternString)
                .orElseThrow(() -> new IllegalStateException("rbac api annotation requires a request path"));
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private record StaticRbacResourceProvider(String appCode,
                                              List<RbacResourceSeed> resources) implements RbacResourceProvider {
    }

}
