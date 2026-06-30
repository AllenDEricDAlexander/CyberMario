package top.egon.mario.nutrition.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.nutrition.dto.request.CreateClanRequest;
import top.egon.mario.nutrition.dto.request.CreateFamilyRequest;
import top.egon.mario.nutrition.dto.response.ClanResponse;
import top.egon.mario.nutrition.dto.response.FamilyResponse;
import top.egon.mario.nutrition.service.ClanFamilyService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

/**
 * Clan and family endpoints for the nutrition MVP.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/nutrition")
@Validated
public class ClanFamilyController extends ReactiveNutritionSupport {

    private final ClanFamilyService clanFamilyService;

    @PostMapping("/clans")
    public Mono<ApiResponse<ClanResponse>> createClan(@Valid @RequestBody CreateClanRequest request,
                                                      @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> clanFamilyService.createClan(request, actorId(principal)));
    }

    @GetMapping("/clans")
    public Mono<ApiResponse<List<ClanResponse>>> clans(@AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> clanFamilyService.listAccessibleClans(actorId(principal)));
    }

    @PostMapping("/families")
    public Mono<ApiResponse<FamilyResponse>> createFamily(@Valid @RequestBody CreateFamilyRequest request,
                                                          @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> clanFamilyService.createFamily(request, actorId(principal)));
    }

    @GetMapping("/families")
    public Mono<ApiResponse<List<FamilyResponse>>> families(@AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> clanFamilyService.listAccessibleFamilies(actorId(principal)));
    }

    @PostMapping("/clans/{clanId}/families/{familyId}")
    public Mono<ApiResponse<FamilyResponse>> associateClanFamily(@PathVariable @Min(1) Long clanId,
                                                                 @PathVariable @Min(1) Long familyId,
                                                                 @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> clanFamilyService.associateClanFamily(clanId, familyId, actorId(principal)));
    }
}
