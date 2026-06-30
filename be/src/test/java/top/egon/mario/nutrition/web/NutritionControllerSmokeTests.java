package top.egon.mario.nutrition.web;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import top.egon.mario.common.api.TraceContext;
import top.egon.mario.nutrition.dto.response.MemberProfileResponse;
import top.egon.mario.nutrition.dto.response.StandardFoodResponse;
import top.egon.mario.nutrition.po.enums.NutritionMemberType;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.service.MemberHealthService;
import top.egon.mario.nutrition.service.RecipeService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NutritionControllerSmokeTests {

    @Test
    void controllersWrapResponsesWithApiResponseAndTraceId() {
        RecipeService recipeService = mock(RecipeService.class);
        RecipeController controller = recipeController(recipeService);
        StandardFoodResponse tomato = standardFood(1L, "Tomato");
        when(recipeService.listStandardFoods(platformAdmin())).thenReturn(List.of(tomato));

        StepVerifier.create(controller.standardFoods(platformAdmin())
                        .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-nutrition-platform")))
                .assertNext(response -> {
                    assertThat(response.code()).isEqualTo("0");
                    assertThat(response.message()).isEqualTo("OK");
                    assertThat(response.traceId()).isEqualTo("trace-nutrition-platform");
                    assertThat(response.data()).containsExactly(tomato);
                })
                .verifyComplete();
    }

    @Test
    void blockingSupportUsesActorIdFromRbacPrincipal() {
        MemberHealthService memberHealthService = mock(MemberHealthService.class);
        MemberHealthController controller = memberHealthController(memberHealthService);
        MemberProfileResponse member = memberProfile(100L, 42L, 77L);
        when(memberHealthService.listMemberProfiles(42L, 77L)).thenReturn(List.of(member));

        StepVerifier.create(controller.members(42L, principal(77L))
                        .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-nutrition-family")))
                .assertNext(response -> {
                    assertThat(response.traceId()).isEqualTo("trace-nutrition-family");
                    assertThat(response.data()).containsExactly(member);
                })
                .verifyComplete();

        verify(memberHealthService).listMemberProfiles(42L, 77L);
    }

    @Test
    void platformAdminApisUsePlatformNutritionPath() {
        assertThat(controllerPaths(RecipeController.class))
                .contains("/api/nutrition/platform/standard-foods")
                .contains("/api/nutrition/families/{familyId}/recipes");

        assertThat(controllerPaths(NutritionImportController.class))
                .containsExactlyInAnyOrder(
                        "/api/nutrition/platform/import-jobs",
                        "/api/nutrition/platform/import-jobs/{jobId}",
                        "/api/nutrition/platform/import-jobs/{jobId}/confirm"
                );

        assertThat(controllerPaths(MemberHealthController.class))
                .contains("/api/nutrition/families/{familyId}/members")
                .allMatch(path -> path.contains("/families/{familyId}/"));
    }

    private static RecipeController recipeController(RecipeService service) {
        RecipeController controller = new RecipeController(service);
        ReflectionTestUtils.invokeMethod(controller, "setBlockingScheduler", Schedulers.immediate());
        return controller;
    }

    private static MemberHealthController memberHealthController(MemberHealthService service) {
        MemberHealthController controller = new MemberHealthController(service);
        ReflectionTestUtils.invokeMethod(controller, "setBlockingScheduler", Schedulers.immediate());
        return controller;
    }

    private static List<String> controllerPaths(Class<?> controllerType) {
        String basePath = firstPath(controllerType.getAnnotation(RequestMapping.class).value(),
                controllerType.getAnnotation(RequestMapping.class).path());
        return Arrays.stream(controllerType.getDeclaredMethods())
                .flatMap(method -> methodPaths(method).stream())
                .map(path -> normalize(basePath, path))
                .toList();
    }

    private static List<String> methodPaths(Method method) {
        GetMapping getMapping = method.getAnnotation(GetMapping.class);
        if (getMapping != null) {
            return paths(getMapping.value(), getMapping.path());
        }
        PostMapping postMapping = method.getAnnotation(PostMapping.class);
        if (postMapping != null) {
            return paths(postMapping.value(), postMapping.path());
        }
        PutMapping putMapping = method.getAnnotation(PutMapping.class);
        if (putMapping != null) {
            return paths(putMapping.value(), putMapping.path());
        }
        return List.of();
    }

    private static List<String> paths(String[] value, String[] path) {
        String[] paths = value.length == 0 ? path : value;
        if (paths.length == 0) {
            return List.of("");
        }
        return Arrays.asList(paths);
    }

    private static String firstPath(String[] value, String[] path) {
        return paths(value, path).getFirst();
    }

    private static String normalize(String basePath, String path) {
        String normalized = (basePath + "/" + path).replaceAll("/{2,}", "/");
        return normalized.endsWith("/") && normalized.length() > 1
                ? normalized.substring(0, normalized.length() - 1)
                : normalized;
    }

    private static StandardFoodResponse standardFood(Long id, String name) {
        return new StandardFoodResponse(id, name, null, "vegetable",
                BigDecimal.valueOf(18), BigDecimal.valueOf(1.2), BigDecimal.valueOf(0.1),
                BigDecimal.valueOf(3.4), NutritionStatus.ACTIVE, Instant.parse("2026-06-30T00:00:00Z"),
                Instant.parse("2026-06-30T00:00:00Z"));
    }

    private static MemberProfileResponse memberProfile(Long id, Long familyId, Long userId) {
        return new MemberProfileResponse(id, familyId, userId, "Luigi", "MALE",
                LocalDate.parse("1990-01-01"), BigDecimal.valueOf(180), BigDecimal.valueOf(72),
                NutritionMemberType.ADULT, true, null, NutritionStatus.ACTIVE,
                Instant.parse("2026-06-30T00:00:00Z"), Instant.parse("2026-06-30T00:00:00Z"));
    }

    private static RbacPrincipal platformAdmin() {
        return new RbacPrincipal(9001L, "nutrition-admin",
                Set.of("NUTRITION_PLATFORM_ADMIN"), Set.of(), "v1");
    }

    private static RbacPrincipal principal(Long userId) {
        return new RbacPrincipal(userId, "user-" + userId, Set.of("NUTRITION_USER"), Set.of(), "v1");
    }
}
