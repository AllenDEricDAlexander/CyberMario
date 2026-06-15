package top.egon.mario.common.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards logging conventions for behavior classes.
 */
class LoggingSourceConventionsTests {

    private static final Path SOURCE_ROOT = Path.of("src/main/java");
    private static final Pattern DIRECT_LOG_CALL_PATTERN =
            Pattern.compile("\\blog\\.(trace|debug|info|warn|error)\\s*\\(");
    private static final List<String> LOGGED_BEHAVIOR_FILES = List.of(
            "top/egon/mario/MarioApplication.java",
            "top/egon/mario/agent/config/AgentConfiguration.java",
            "top/egon/mario/agent/config/ArxivToolConfig.java",
            "top/egon/mario/agent/hooks/LoggingHook.java",
            "top/egon/mario/agent/interceptor/DynamicPromptInterceptor.java",
            "top/egon/mario/agent/interceptor/ToolMonitorInterceptor.java",
            "top/egon/mario/agent/model/service/impl/DefaultModelAuditService.java",
            "top/egon/mario/agent/service/impl/ReactAgentChatService.java",
            "top/egon/mario/agent/tools/arxiv/ArxivImportService.java",
            "top/egon/mario/agent/tools/arxiv/ArxivTools.java",
            "top/egon/mario/common/filter/TraceWebFilter.java",
            "top/egon/mario/config/GlobalExceptionHandler.java",
            "top/egon/mario/config/JpaAuditingConfig.java",
            "top/egon/mario/rbac/config/RbacSecurityConfig.java",
            "top/egon/mario/rbac/application/RbacAuthApplication.java",
            "top/egon/mario/rbac/service/ApiRuleMatcher.java",
            "top/egon/mario/rbac/service/impl/RbacAuditServiceImpl.java",
            "top/egon/mario/rbac/service/bootstrap/RbacAdminBootstrap.java",
            "top/egon/mario/rbac/service/impl/RbacEffectivePermissionServiceImpl.java",
            "top/egon/mario/rbac/service/impl/RbacPermissionServiceImpl.java",
            "top/egon/mario/rbac/service/impl/RbacRoleServiceImpl.java",
            "top/egon/mario/rbac/service/impl/RbacUserServiceImpl.java",
            "top/egon/mario/rbac/service/RoleHierarchyResolver.java",
            "top/egon/mario/rbac/service/impl/RolePermissionMergeServiceImpl.java",
            "top/egon/mario/rbac/service/cache/RbacBloomGuards.java",
            "top/egon/mario/rbac/service/cache/RbacCacheEvictionBroadcaster.java",
            "top/egon/mario/rbac/service/cache/RbacCacheEvictionSubscriber.java",
            "top/egon/mario/rbac/service/cache/RbacPermissionRedisCache.java",
            "top/egon/mario/rbac/service/cache/RbacRedisCacheInvalidator.java",
            "top/egon/mario/rbac/service/cache/RbacTokenCache.java",
            "top/egon/mario/rbac/service/cache/RbacTwoLevelCache.java",
            "top/egon/mario/rbac/service/cache/RbacTwoLevelCacheManager.java",
            "top/egon/mario/rbac/service/security/DynamicAuthorizationManager.java",
            "top/egon/mario/rbac/service/security/JwtAuthenticationWebFilter.java",
            "top/egon/mario/rbac/service/security/impl/JwtTokenServiceImpl.java",
            "top/egon/mario/rbac/service/security/RbacApiRuleCache.java",
            "top/egon/mario/rbac/web/AdminApiPermissionController.java",
            "top/egon/mario/rbac/web/AdminButtonController.java",
            "top/egon/mario/rbac/web/AdminMenuController.java",
            "top/egon/mario/rbac/web/AdminPermissionController.java",
            "top/egon/mario/rbac/web/AdminRoleController.java",
            "top/egon/mario/rbac/web/AdminUserController.java",
            "top/egon/mario/rbac/web/AuthController.java",
            "top/egon/mario/rbac/web/MeController.java",
            "top/egon/mario/rbac/web/ReactiveRbacSupport.java",
            "top/egon/mario/web/ChatController.java"
    );

    @Test
    void behaviorClassesUseLombokSlf4j() throws IOException {
        for (String file : LOGGED_BEHAVIOR_FILES) {
            String source = source(file);

            assertThat(source)
                    .as(file)
                    .contains("import lombok.extern.slf4j.Slf4j;")
                    .contains("@Slf4j");
        }
    }

    @Test
    void productionCodeDoesNotUseConsolePrints() throws IOException {
        try (var files = Files.walk(SOURCE_ROOT)) {
            List<String> offenders = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> containsConsolePrint(path))
                    .map(SOURCE_ROOT::relativize)
                    .map(Path::toString)
                    .toList();

            assertThat(offenders).isEmpty();
        }
    }

    @Test
    void productionCodeUsesLogUtilForLogEvents() throws IOException {
        try (var files = Files.walk(SOURCE_ROOT)) {
            List<String> offenders = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !SOURCE_ROOT.relativize(path).toString()
                            .equals("top/egon/mario/common/utils/LogUtil.java"))
                    .filter(path -> containsDirectLogCall(path))
                    .map(SOURCE_ROOT::relativize)
                    .map(Path::toString)
                    .toList();

            assertThat(offenders).isEmpty();
        }
    }

    private static String source(String file) throws IOException {
        return Files.readString(SOURCE_ROOT.resolve(file));
    }

    private static boolean containsConsolePrint(Path path) {
        try {
            String source = Files.readString(path);
            return source.contains("System.out.print") || source.contains("System.err.print");
        } catch (IOException e) {
            throw new IllegalStateException("read source failed: " + path, e);
        }
    }

    private static boolean containsDirectLogCall(Path path) {
        try {
            String source = Files.readString(path);
            return DIRECT_LOG_CALL_PATTERN.matcher(source).find();
        } catch (IOException e) {
            throw new IllegalStateException("read source failed: " + path, e);
        }
    }

}
