package top.egon.mario.nutrition.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.nutrition.dto.request.CreateMemberProfileRequest;
import top.egon.mario.nutrition.dto.request.UpdateHealthProfileRequest;
import top.egon.mario.nutrition.dto.response.HealthProfileResponse;
import top.egon.mario.nutrition.dto.response.MemberProfileResponse;
import top.egon.mario.nutrition.service.MemberHealthService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

/**
 * Member and health profile endpoints for the nutrition MVP.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/nutrition/families/{familyId}")
@Validated
public class MemberHealthController extends ReactiveNutritionSupport {

    private final MemberHealthService memberHealthService;

    @PostMapping("/members")
    public Mono<ApiResponse<MemberProfileResponse>> createMemberProfile(@PathVariable @Min(1) Long familyId,
                                                                       @Valid @RequestBody CreateMemberProfileRequest request,
                                                                       @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> memberHealthService.createMemberProfile(familyId, request, actorId(principal)));
    }

    @GetMapping("/members")
    public Mono<ApiResponse<List<MemberProfileResponse>>> members(@PathVariable @Min(1) Long familyId,
                                                                  @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> memberHealthService.listMemberProfiles(familyId, actorId(principal)));
    }

    @GetMapping("/health-profiles")
    public Mono<ApiResponse<List<HealthProfileResponse>>> healthProfiles(@PathVariable @Min(1) Long familyId,
                                                                         @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> memberHealthService.listFamilyHealthProfiles(familyId, actorId(principal)));
    }

    @PutMapping("/members/{memberProfileId}/health-profile")
    public Mono<ApiResponse<HealthProfileResponse>> updateHealthProfile(@PathVariable @Min(1) Long familyId,
                                                                        @PathVariable @Min(1) Long memberProfileId,
                                                                        @Valid @RequestBody UpdateHealthProfileRequest request,
                                                                        @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> memberHealthService.updateHealthProfile(
                familyId, memberProfileId, request, actorId(principal)));
    }
}
