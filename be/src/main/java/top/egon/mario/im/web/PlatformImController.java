package top.egon.mario.im.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.im.platform.PlatformImFacade;
import top.egon.mario.im.platform.dto.PlatformBootstrapView;
import top.egon.mario.im.platform.dto.PlatformConversationView;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

@RestController
@RequestMapping("/api/im/platform")
public class PlatformImController extends ReactiveImSupport {

    private final PlatformImFacade platformImFacade;

    public PlatformImController(PlatformImFacade platformImFacade) {
        this.platformImFacade = platformImFacade;
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
}
