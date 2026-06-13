package top.egon.mario.rbac.service.resource;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import top.egon.mario.common.utils.LogUtil;
import top.egon.mario.rbac.service.resource.model.RbacResourceSeed;
import top.egon.mario.rbac.service.resource.model.RbacRolePresetSeed;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects local resource providers and delegates DB writes to the synchronizer.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 20)
@RequiredArgsConstructor
@Slf4j
public class RbacResourceSyncRunner implements ApplicationRunner {

    private final RbacResourceSyncProperties properties;
    private final RbacResourceSynchronizer synchronizer;
    private final List<RbacResourceProvider> providers;
    private final AnnotationRbacResourceProvider annotationProvider;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            LogUtil.debug(log).log("rbac resource synchronization skipped, enabled=false");
            return;
        }
        List<RbacResourceProvider> allProviders = new ArrayList<>(providers);
        allProviders.addAll(annotationProvider.providers());
        List<ProviderManifest> manifests = allProviders.stream()
                .map(provider -> new ProviderManifest(provider.appCode(), provider.resources(), provider.rolePresets()))
                .toList();
        for (ProviderManifest manifest : manifests) {
            if (!manifest.resources().isEmpty()) {
                synchronizer.synchronize(manifest.appCode(), manifest.resources(), List.of());
            }
        }
        for (ProviderManifest manifest : manifests) {
            if (!manifest.rolePresets().isEmpty()) {
                synchronizer.synchronize(manifest.appCode(), List.of(), manifest.rolePresets());
            }
        }
    }

    private record ProviderManifest(String appCode, List<RbacResourceSeed> resources,
                                    List<RbacRolePresetSeed> rolePresets) {

        private ProviderManifest {
            resources = resources == null ? List.of() : List.copyOf(resources);
            rolePresets = rolePresets == null ? List.of() : List.copyOf(rolePresets);
        }

    }

}
