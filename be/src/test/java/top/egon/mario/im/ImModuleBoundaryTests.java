package top.egon.mario.im;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ImModuleBoundaryTests {

    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath().normalize();
    private static final Path MAIN_SOURCE_ROOT = PROJECT_ROOT.resolve("src/main/java/top/egon/mario");
    private static final Path TEST_SOURCE_ROOT = PROJECT_ROOT.resolve("src/test/java/top/egon/mario");
    private static final Path MIGRATION_ROOT = PROJECT_ROOT.resolve("src/main/resources/db");
    private static final String IM_PACKAGE_PATH = "top/egon/mario/im";
    private static final Pattern IM_IMPORT = Pattern.compile("^import\\s+(top\\.egon\\.mario\\.im\\.[\\w.]+);");
    private static final Pattern IM_TABLE_REFERENCE = Pattern.compile("\\bim_[a-z0-9_]+\\b");
    private static final Pattern VERSIONED_MIGRATION = Pattern.compile("V(\\d+)__.*\\.sql");

    private static final Set<String> ALLOWED_IM_POLICY_IMPORTS = Set.of(
            "top.egon.mario.im.policy.ImPrincipal",
            "top.egon.mario.im.policy.ImAccessContext",
            "top.egon.mario.im.policy.SendPolicy",
            "top.egon.mario.im.policy.VisibilityPolicy",
            "top.egon.mario.im.policy.PolicyRegistry"
    );

    private static final Set<String> ALLOWED_IM_REALTIME_IMPORTS = Set.of(
            "top.egon.mario.im.realtime.ImFrame"
    );

    @Test
    void upstreamProductionSourcesUseOnlyDocumentedImBoundaryImports() throws IOException {
        List<String> violations = javaSourcesOutsideIm(MAIN_SOURCE_ROOT)
                .flatMap(path -> imImports(path).stream()
                        .filter(imImport -> !allowedUpstreamImport(imImport.importName()))
                        .map(SourceImport::format))
                .toList();

        assertThat(violations)
                .describedAs("Upstream production sources must use only IM facade DTOs and documented policy/realtime APIs")
                .isEmpty();
    }

    @Test
    void upstreamTestsDoNotDependOnImPersistenceOrLegacyInternals() throws IOException {
        List<String> violations = javaSourcesOutsideIm(TEST_SOURCE_ROOT)
                .flatMap(path -> imImports(path).stream()
                        .filter(imImport -> forbiddenUpstreamTestImport(imImport.importName()))
                        .map(SourceImport::format))
                .toList();

        assertThat(violations)
                .describedAs("Upstream tests must not compile against IM persistence, context, legacy, or old core service internals")
                .isEmpty();
    }

    @Test
    void legacyImCoreTypesAreRemovedFromProductionSources() throws IOException {
        List<String> violations = javaSources(MAIN_SOURCE_ROOT)
                .flatMap(path -> linesContaining(path,
                        "top.egon.mario.im.service.ImCoreService",
                        "ImCoreService",
                        "top.egon.mario.im.legacy",
                        "LegacyImFacade",
                        "top.egon.mario.im.context",
                        "ImSendPolicy",
                        "ImVisibilityPolicy",
                        "ImPolicyRegistry").stream())
                .toList();

        assertThat(violations)
                .describedAs("Legacy IM context/facade/core service and old policy SPI must not remain in production code")
                .isEmpty();
    }

    @Test
    void imPackageDoesNotImportClocktowerPackage() throws IOException {
        List<String> violations = javaSources(MAIN_SOURCE_ROOT.resolve("im"))
                .flatMap(path -> linesContaining(path, "import top.egon.mario.clocktower.").stream())
                .toList();

        assertThat(violations)
                .describedAs("IM module must not depend on Clocktower")
                .isEmpty();
    }

    @Test
    void onlyV30VersionedMigrationsModifyImSchemaAfterV26() throws IOException {
        List<String> violations = sqlSources(MIGRATION_ROOT)
                .filter(path -> version(path) > 26)
                .filter(path -> version(path) != 30)
                .flatMap(path -> linesMatching(path, IM_TABLE_REFERENCE).stream())
                .toList();

        assertThat(violations)
                .describedAs("After V26, only V30 may contain IM schema/table references in versioned migrations")
                .isEmpty();
    }

    private static Stream<Path> javaSourcesOutsideIm(Path root) throws IOException {
        return javaSources(root)
                .filter(path -> !path.toString().contains(IM_PACKAGE_PATH));
    }

    private static Stream<Path> javaSources(Path root) throws IOException {
        if (!Files.exists(root)) {
            return Stream.empty();
        }
        return Files.walk(root)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"));
    }

    private static Stream<Path> sqlSources(Path root) throws IOException {
        if (!Files.exists(root)) {
            return Stream.empty();
        }
        return Files.walk(root)
                .filter(Files::isRegularFile)
                .filter(path -> version(path) > 0);
    }

    private static List<SourceImport> imImports(Path path) {
        try {
            List<String> lines = Files.readAllLines(path);
            return Stream.iterate(0, index -> index + 1)
                    .limit(lines.size())
                    .map(index -> new SourceLine(path, index + 1, lines.get(index)))
                    .map(line -> {
                        Matcher matcher = IM_IMPORT.matcher(line.text().trim());
                        return matcher.find() ? new SourceImport(line.path(), line.lineNo(), matcher.group(1)) : null;
                    })
                    .filter(sourceImport -> sourceImport != null)
                    .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read " + path, ex);
        }
    }

    private static List<String> linesContaining(Path path, String... needles) {
        try {
            List<String> lines = Files.readAllLines(path);
            return Stream.iterate(0, index -> index + 1)
                    .limit(lines.size())
                    .filter(index -> containsAny(lines.get(index), needles))
                    .map(index -> format(path, index + 1, lines.get(index)))
                    .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read " + path, ex);
        }
    }

    private static List<String> linesMatching(Path path, Pattern pattern) {
        try {
            List<String> lines = Files.readAllLines(path);
            return Stream.iterate(0, index -> index + 1)
                    .limit(lines.size())
                    .filter(index -> pattern.matcher(lines.get(index)).find())
                    .map(index -> format(path, index + 1, lines.get(index)))
                    .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read " + path, ex);
        }
    }

    private static boolean allowedUpstreamImport(String importName) {
        return importName.startsWith("top.egon.mario.im.facade.")
                || ALLOWED_IM_POLICY_IMPORTS.contains(importName)
                || ALLOWED_IM_REALTIME_IMPORTS.contains(importName);
    }

    private static boolean forbiddenUpstreamTestImport(String importName) {
        return importName.startsWith("top.egon.mario.im.po.")
                || importName.startsWith("top.egon.mario.im.repository.")
                || importName.startsWith("top.egon.mario.im.context.")
                || importName.startsWith("top.egon.mario.im.legacy.")
                || "top.egon.mario.im.service.ImCoreService".equals(importName)
                || "top.egon.mario.im.policy.ImSendPolicy".equals(importName)
                || "top.egon.mario.im.policy.ImVisibilityPolicy".equals(importName)
                || "top.egon.mario.im.policy.ImPolicyRegistry".equals(importName);
    }

    private static boolean containsAny(String line, String... needles) {
        for (String needle : needles) {
            if (line.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static int version(Path path) {
        Matcher matcher = VERSIONED_MIGRATION.matcher(path.getFileName().toString());
        return matcher.matches() ? Integer.parseInt(matcher.group(1)) : -1;
    }

    private static String format(Path path, int lineNo, String line) {
        return PROJECT_ROOT.relativize(path) + ":" + lineNo + " " + line.trim();
    }

    private record SourceLine(Path path, int lineNo, String text) {
    }

    private record SourceImport(Path path, int lineNo, String importName) {

        String format() {
            return ImModuleBoundaryTests.format(path, lineNo, "import " + importName + ";");
        }
    }
}
