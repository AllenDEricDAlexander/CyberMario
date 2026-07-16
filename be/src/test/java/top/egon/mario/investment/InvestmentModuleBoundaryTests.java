package top.egon.mario.investment;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class InvestmentModuleBoundaryTests {

    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath().normalize();
    private static final Path MAIN_SOURCE_ROOT = PROJECT_ROOT.resolve("src/main/java/top/egon/mario");
    private static final Path INVESTMENT_SOURCE_ROOT = MAIN_SOURCE_ROOT.resolve("investment");
    private static final Set<String> FORBIDDEN_DOMAIN_IMPORTS = Set.of(
            "import top.egon.mario.clocktower.",
            "import top.egon.mario.im.",
            "import top.egon.mario.nutrition.",
            "import top.egon.mario.rag.",
            "import top.egon.mario.room."
    );

    @Test
    void investmentDoesNotDependOnUnrelatedBusinessModules() throws IOException {
        assertThat(linesContaining(javaSources(INVESTMENT_SOURCE_ROOT), FORBIDDEN_DOMAIN_IMPORTS))
                .describedAs("Investment may reuse shared infrastructure, RBAC and generic Agent runtime, not other domains")
                .isEmpty();
    }

    @Test
    void investmentCommonContractsStayPersistenceFree() throws IOException {
        Path commonRoot = INVESTMENT_SOURCE_ROOT.resolve("common");
        List<String> violations = linesContaining(
                javaSources(commonRoot)
                        .filter(path -> !path.startsWith(commonRoot.resolve("job/po")))
                        .filter(path -> !path.startsWith(commonRoot.resolve("job/repository"))),
                Set.of("import jakarta.persistence.", "@Entity", "@Table"));

        assertThat(violations)
                .describedAs("Investment common contracts must not expose persistence entities outside the durable job adapter")
                .isEmpty();
    }

    @Test
    void upstreamProductionSourcesDoNotImportInvestmentInternals() throws IOException {
        List<String> violations = javaSources(MAIN_SOURCE_ROOT)
                .filter(path -> !path.startsWith(INVESTMENT_SOURCE_ROOT))
                .filter(path -> !path.endsWith(Path.of("config/GlobalExceptionHandler.java")))
                .flatMap(path -> {
                    try {
                        return Files.readAllLines(path).stream()
                                .filter(line -> line.contains("import top.egon.mario.investment."))
                                .map(line -> PROJECT_ROOT.relativize(path) + " " + line.trim());
                    } catch (IOException ex) {
                        throw new IllegalStateException("Failed to read " + path, ex);
                    }
                })
                .toList();

        assertThat(violations)
                .describedAs("Other modules must not reach into Investment internals")
                .isEmpty();
    }

    private static Stream<Path> javaSources(Path root) throws IOException {
        if (!Files.exists(root)) {
            return Stream.empty();
        }
        return Files.walk(root)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"));
    }

    private static List<String> linesContaining(Stream<Path> paths, Set<String> needles) {
        return paths.flatMap(path -> {
                    try {
                        List<String> lines = Files.readAllLines(path);
                        return Stream.iterate(0, index -> index + 1)
                                .limit(lines.size())
                                .filter(index -> needles.stream().anyMatch(lines.get(index)::contains))
                                .map(index -> PROJECT_ROOT.relativize(path) + ":" + (index + 1) + " " + lines.get(index).trim());
                    } catch (IOException ex) {
                        throw new IllegalStateException("Failed to read " + path, ex);
                    }
                })
                .toList();
    }
}
