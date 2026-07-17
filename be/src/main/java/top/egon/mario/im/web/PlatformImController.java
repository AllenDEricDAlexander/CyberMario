package top.egon.mario.im.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.im.platform.PlatformImFacade;
import top.egon.mario.im.platform.PlatformRoomFacade;
import top.egon.mario.im.platform.dto.PlatformBootstrapView;
import top.egon.mario.im.platform.dto.PlatformConversationView;
import top.egon.mario.im.facade.dto.view.ChannelView;
import top.egon.mario.im.facade.dto.view.GroupView;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

@RestController
@RequestMapping("/api/im/platform")
public class PlatformImController extends ReactiveImSupport {

    private final PlatformImFacade platformImFacade;
    private final PlatformRoomFacade platformRoomFacade;

    public PlatformImController(PlatformImFacade platformImFacade, PlatformRoomFacade platformRoomFacade) {
        this.platformImFacade = platformImFacade;
        this.platformRoomFacade = platformRoomFacade;
    }

    @GetMapping("/bootstrap")
    public Mono<ApiResponse<PlatformBootstrapView>> bootstrap(
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> platformImFacade.bootstrap(imPrincipal(principal)));
    }

    @GetMapping("/conversations")
    public Mono<ApiResponse<List<PlatformConversationView>>> listConversations(
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> platformImFacade.listConversations(imPrincipal(principal)));
    }

    @PostMapping("/channels")
    public Mono<ApiResponse<ChannelView>> createChannel(
            @AuthenticationPrincipal RbacPrincipal principal,
            @RequestBody SurfaceCreateRequest request) {
        return blocking(() -> platformRoomFacade.createChannel(
                imPrincipal(principal),
                request == null ? null : request.name(),
                request == null ? null : request.metadataJson()));
    }

    @GetMapping("/channels")
    public Mono<ApiResponse<List<ChannelView>>> listChannels(
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> platformRoomFacade.listChannels(imPrincipal(principal)));
    }

    @PostMapping("/groups")
    public Mono<ApiResponse<GroupView>> createStandaloneGroup(
            @AuthenticationPrincipal RbacPrincipal principal,
            @RequestBody SurfaceCreateRequest request) {
        return blocking(() -> platformRoomFacade.createStandaloneGroup(
                imPrincipal(principal),
                request == null ? null : request.name(),
                request == null ? null : request.metadataJson()));
    }

    @GetMapping("/groups")
    public Mono<ApiResponse<List<GroupView>>> listStandaloneGroups(
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> platformRoomFacade.listGroups(imPrincipal(principal)));
    }

    @PostMapping("/channels/{channelId}/groups")
    public Mono<ApiResponse<GroupView>> createChannelGroup(
            @AuthenticationPrincipal RbacPrincipal principal,
            @PathVariable Long channelId,
            @RequestBody ChannelGroupCreateRequest request) {
        return blocking(() -> platformRoomFacade.createChannelGroup(
                imPrincipal(principal),
                channelId,
                request == null ? null : request.name(),
                request == null ? null : request.joinPolicy(),
                request == null ? null : request.metadataJson()));
    }

    @GetMapping("/channels/{channelId}/groups")
    public Mono<ApiResponse<List<GroupView>>> listChannelGroups(
            @AuthenticationPrincipal RbacPrincipal principal,
            @PathVariable Long channelId) {
        return blocking(() -> platformRoomFacade.listChannelGroups(imPrincipal(principal), channelId));
    }

    public record SurfaceCreateRequest(String name, String metadataJson) {
    }

    public record ChannelGroupCreateRequest(String name, String joinPolicy, String metadataJson) {
    }
}
