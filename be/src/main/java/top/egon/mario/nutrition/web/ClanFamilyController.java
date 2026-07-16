package top.egon.mario.nutrition.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import reactor.core.publisher.Mono;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.nutrition.dto.request.CreateClanRequest;
import top.egon.mario.nutrition.dto.request.CreateDataGrantRequest;
import top.egon.mario.nutrition.dto.request.CreateFamilyRequest;
import top.egon.mario.nutrition.dto.request.CreateScopedRoleBindingRequest;
import top.egon.mario.nutrition.dto.request.UpdateDataGrantRequest;
import top.egon.mario.nutrition.dto.request.UpdateFamilySettingsRequest;
import top.egon.mario.nutrition.dto.response.ClanResponse;
import top.egon.mario.nutrition.dto.response.ClanFamilyRelationResponse;
import top.egon.mario.nutrition.dto.response.DataGrantResponse;
import top.egon.mario.nutrition.dto.response.FamilyResponse;
import top.egon.mario.nutrition.dto.response.ScopedRoleBindingResponse;
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
        return blocking(() -> clanFamilyService.createFamily(
                request, actorId(principal), actorUsername(principal)));
    }

    @GetMapping("/families")
    public Mono<ApiResponse<List<FamilyResponse>>> families(@AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> clanFamilyService.listAccessibleFamilies(actorId(principal)));
    }

    @DeleteMapping("/families/{familyId}")
    public Mono<ApiResponse<Void>> deleteFamily(@PathVariable @Min(1) Long familyId,
                                                @AuthenticationPrincipal RbacPrincipal principal) {
        return blockingVoid(() -> clanFamilyService.deleteFamily(familyId, actorId(principal)));
    }

    @GetMapping("/families/{familyId}/settings")
    public Mono<ApiResponse<FamilyResponse>> getFamilySettings(@PathVariable @Min(1) Long familyId,
                                                               @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> clanFamilyService.getFamilySettings(familyId, actorId(principal)));
    }

    @PutMapping("/families/{familyId}/settings")
    public Mono<ApiResponse<FamilyResponse>> updateFamilySettings(
            @PathVariable @Min(1) Long familyId,
            @Valid @RequestBody UpdateFamilySettingsRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> clanFamilyService.updateFamilySettings(familyId, request, actorId(principal)));
    }

    @GetMapping("/families/{familyId}/role-bindings")
    public Mono<ApiResponse<List<ScopedRoleBindingResponse>>> roleBindings(
            @PathVariable @Min(1) Long familyId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> clanFamilyService.listRoleBindings(familyId, actorId(principal)));
    }

    @PostMapping("/families/{familyId}/role-bindings")
    public Mono<ApiResponse<ScopedRoleBindingResponse>> createRoleBinding(
            @PathVariable @Min(1) Long familyId,
            @Valid @RequestBody CreateScopedRoleBindingRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> clanFamilyService.createRoleBinding(familyId, request, actorId(principal)));
    }

    @PutMapping("/families/{familyId}/role-bindings/{bindingId}")
    public Mono<ApiResponse<ScopedRoleBindingResponse>> updateRoleBinding(
            @PathVariable @Min(1) Long familyId,
            @PathVariable @Min(1) Long bindingId,
            @Valid @RequestBody CreateScopedRoleBindingRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> clanFamilyService.updateRoleBinding(
                familyId, bindingId, request, actorId(principal)));
    }

    @DeleteMapping("/families/{familyId}/role-bindings/{bindingId}")
    public Mono<ApiResponse<Void>> revokeRoleBinding(@PathVariable @Min(1) Long familyId,
                                                     @PathVariable @Min(1) Long bindingId,
                                                     @AuthenticationPrincipal RbacPrincipal principal) {
        return blockingVoid(() -> clanFamilyService.revokeRoleBinding(
                familyId, bindingId, actorId(principal)));
    }

    @GetMapping("/families/{familyId}/data-grants")
    public Mono<ApiResponse<List<DataGrantResponse>>> dataGrants(
            @PathVariable @Min(1) Long familyId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> clanFamilyService.listDataGrants(familyId, actorId(principal)));
    }

    @PostMapping("/families/{familyId}/data-grants")
    public Mono<ApiResponse<DataGrantResponse>> createDataGrant(
            @PathVariable @Min(1) Long familyId,
            @Valid @RequestBody CreateDataGrantRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> clanFamilyService.createDataGrant(familyId, request, actorId(principal)));
    }

    @PutMapping("/families/{familyId}/data-grants/{grantId}")
    public Mono<ApiResponse<DataGrantResponse>> updateDataGrant(
            @PathVariable @Min(1) Long familyId,
            @PathVariable @Min(1) Long grantId,
            @Valid @RequestBody UpdateDataGrantRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> clanFamilyService.updateDataGrant(
                familyId, grantId, request, actorId(principal)));
    }

    @DeleteMapping("/families/{familyId}/data-grants/{grantId}")
    public Mono<ApiResponse<Void>> revokeDataGrant(@PathVariable @Min(1) Long familyId,
                                                   @PathVariable @Min(1) Long grantId,
                                                   @AuthenticationPrincipal RbacPrincipal principal) {
        return blockingVoid(() -> clanFamilyService.revokeDataGrant(
                familyId, grantId, actorId(principal)));
    }

    @GetMapping("/families/{familyId}/clan-relations")
    public Mono<ApiResponse<List<ClanFamilyRelationResponse>>> clanFamilyRelations(
            @PathVariable @Min(1) Long familyId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> clanFamilyService.listClanFamilyRelations(familyId, actorId(principal)));
    }

    @DeleteMapping("/families/{familyId}/clan-relations/{relationId}")
    public Mono<ApiResponse<Void>> removeClanFamilyRelation(@PathVariable @Min(1) Long familyId,
                                                           @PathVariable @Min(1) Long relationId,
                                                           @AuthenticationPrincipal RbacPrincipal principal) {
        return blockingVoid(() -> clanFamilyService.removeClanFamilyRelation(
                familyId, relationId, actorId(principal)));
    }

    @PostMapping("/clans/{clanId}/families/{familyId}")
    public Mono<ApiResponse<FamilyResponse>> associateClanFamily(@PathVariable @Min(1) Long clanId,
                                                                 @PathVariable @Min(1) Long familyId,
                                                                 @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> clanFamilyService.associateClanFamily(clanId, familyId, actorId(principal)));
    }
}
