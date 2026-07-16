package top.egon.mario.nutrition.web;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import top.egon.mario.nutrition.dto.request.AssignProfileGuardianRequest;
import top.egon.mario.nutrition.dto.request.CreateFamilyRequest;
import top.egon.mario.nutrition.dto.request.UpdateFamilySettingsRequest;
import top.egon.mario.nutrition.dto.response.FamilyResponse;
import top.egon.mario.nutrition.dto.response.ScopedRoleBindingResponse;
import top.egon.mario.nutrition.po.enums.NutritionMealType;
import top.egon.mario.nutrition.po.enums.NutritionRoleCode;
import top.egon.mario.nutrition.po.enums.NutritionScopeType;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.po.enums.NutritionSubjectType;
import top.egon.mario.nutrition.service.ClanFamilyService;
import top.egon.mario.nutrition.service.MemberHealthService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies family administration routes and principal delegation.
 */
class NutritionAdministrationControllerTests {

    @Test
    void familyAdministrationRoutesAreExposedUnderFamilyScope() {
        assertThat(controllerPaths(ClanFamilyController.class))
                .contains(
                        "/api/nutrition/families/{familyId}/settings",
                        "/api/nutrition/families/{familyId}/role-bindings",
                        "/api/nutrition/families/{familyId}/role-bindings/{bindingId}",
                        "/api/nutrition/families/{familyId}/data-grants",
                        "/api/nutrition/families/{familyId}/data-grants/{grantId}",
                        "/api/nutrition/families/{familyId}/clan-relations",
                        "/api/nutrition/families/{familyId}/clan-relations/{relationId}"
                );
        assertThat(controllerPaths(MemberHealthController.class))
                .contains(
                        "/api/nutrition/families/{familyId}/members/{memberProfileId}",
                        "/api/nutrition/families/{familyId}/members/{memberProfileId}/bind-user",
                        "/api/nutrition/families/{familyId}/members/{memberProfileId}/guardians",
                        "/api/nutrition/families/{familyId}/members/{memberProfileId}/guardians/{bindingId}"
                );
    }

    @Test
    void settingsAndRoleRevokeDelegateAuthenticatedActor() {
        ClanFamilyService service = mock(ClanFamilyService.class);
        ClanFamilyController controller = clanFamilyController(service);
        UpdateFamilySettingsRequest request = new UpdateFamilySettingsRequest(
                "Shanghai", "CNY", List.of(NutritionMealType.DINNER),
                true, LocalTime.of(6, 30), true, false);
        FamilyResponse response = family(42L);
        when(service.updateFamilySettings(42L, request, 7001L)).thenReturn(response);

        StepVerifier.create(controller.updateFamilySettings(42L, request, principal(7001L)))
                .assertNext(apiResponse -> assertThat(apiResponse.data()).isEqualTo(response))
                .verifyComplete();
        StepVerifier.create(controller.revokeRoleBinding(42L, 81L, principal(7001L)))
                .assertNext(apiResponse -> assertThat(apiResponse.data()).isNull())
                .verifyComplete();

        verify(service).updateFamilySettings(42L, request, 7001L);
        verify(service).revokeRoleBinding(42L, 81L, 7001L);
    }

    @Test
    void familyCreationDelegatesAuthenticatedUsername() {
        ClanFamilyService service = mock(ClanFamilyService.class);
        ClanFamilyController controller = clanFamilyController(service);
        CreateFamilyRequest request = new CreateFamilyRequest(
                "Mario Family", "Shanghai", "CNY", List.of("DINNER"), "ignored");
        FamilyResponse response = family(42L);
        when(service.createFamily(request, 7001L, "user-7001")).thenReturn(response);

        StepVerifier.create(controller.createFamily(request, principal(7001L)))
                .assertNext(apiResponse -> assertThat(apiResponse.data()).isEqualTo(response))
                .verifyComplete();

        verify(service).createFamily(request, 7001L, "user-7001");
    }

    @Test
    void guardianAssignmentDelegatesAuthenticatedActor() {
        MemberHealthService service = mock(MemberHealthService.class);
        MemberHealthController controller = memberHealthController(service);
        AssignProfileGuardianRequest request = new AssignProfileGuardianRequest(7002L);
        ScopedRoleBindingResponse response = new ScopedRoleBindingResponse(
                91L, NutritionSubjectType.USER, 7002L, NutritionRoleCode.PROFILE_GUARDIAN,
                NutritionScopeType.MEMBER_PROFILE, 51L, NutritionStatus.ACTIVE,
                Instant.parse("2026-07-13T00:00:00Z"), Instant.parse("2026-07-13T00:00:00Z"));
        when(service.assignProfileGuardian(42L, 51L, request, 7001L)).thenReturn(response);

        Mono<?> result = controller.assignProfileGuardian(42L, 51L, request, principal(7001L));

        StepVerifier.create(result).expectNextCount(1).verifyComplete();
        verify(service).assignProfileGuardian(42L, 51L, request, 7001L);
    }

    private static ClanFamilyController clanFamilyController(ClanFamilyService service) {
        ClanFamilyController controller = new ClanFamilyController(service);
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
        DeleteMapping deleteMapping = method.getAnnotation(DeleteMapping.class);
        if (deleteMapping != null) {
            return paths(deleteMapping.value(), deleteMapping.path());
        }
        return List.of();
    }

    private static List<String> paths(String[] value, String[] path) {
        String[] paths = value.length == 0 ? path : value;
        return paths.length == 0 ? List.of("") : Arrays.asList(paths);
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

    private static FamilyResponse family(Long id) {
        return new FamilyResponse(id, "Mario Family", 7001L, "Shanghai", "CNY",
                List.of("DINNER"), true, LocalTime.of(6, 30), true, false,
                NutritionStatus.ACTIVE, 51L, Instant.parse("2026-07-13T00:00:00Z"),
                Instant.parse("2026-07-13T00:00:00Z"));
    }

    private static RbacPrincipal principal(Long userId) {
        return new RbacPrincipal(userId, "user-" + userId, Set.of("NUTRITION_USER"), Set.of(), "v1");
    }
}
