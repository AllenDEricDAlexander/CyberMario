package top.egon.mario.investment;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prevents private Investment facts and throwable payloads from entering application logs.
 */
class InvestmentLoggingConventionsTests {

    private static final Path INVESTMENT_SOURCE =
            Path.of("src/main/java/top/egon/mario/investment");
    private static final Pattern LOG_CALL = Pattern.compile(
            "LogUtil\\.(?:trace|debug|info|warn|error)\\(log\\)\\.log\\((?s:.*?)\\);", Pattern.MULTILINE);
    private static final List<String> PRIVATE_TERMS = List.of(
            "input_json", "result_json", "inputsnapshot", "effectiveconfig",
            "toolarguments", "toolresult", "requestmessages", "prompttext",
            "contentmarkdown", "indicatorsnapshot", "portfolio", "walletbalance",
            "availablebalance", "positionquantity", "thesis", "risksjson",
            "invalidationjson", "user_message", "final_message");

    @Test
    void logStatementsContainOnlyOperationalIdentifiersAndRedactedErrors() throws Exception {
        List<LogStatement> statements = logStatements();

        assertThat(statements).isNotEmpty();
        assertThat(statements).allSatisfy(statement -> {
            String normalized = statement.source().toLowerCase(Locale.ROOT);
            assertThat(normalized).as(statement.path().toString())
                    .doesNotContain(PRIVATE_TERMS.toArray(String[]::new))
                    .doesNotContain("exception);")
                    .doesNotContain("ex);");
        });
    }

    @Test
    void privateAgentReportAndTradingServicesDoNotLogDomainPayloads() throws Exception {
        for (String directory : List.of("agent", "research/report", "trading/service")) {
            String source = readSources(INVESTMENT_SOURCE.resolve(directory));
            assertThat(LOG_CALL.matcher(source).find()).as(directory + " log calls").isFalse();
        }
    }

    private List<LogStatement> logStatements() throws Exception {
        List<LogStatement> statements = new ArrayList<>();
        try (var paths = Files.walk(INVESTMENT_SOURCE)) {
            for (Path path : paths.filter(value -> value.toString().endsWith(".java")).sorted().toList()) {
                String source = Files.readString(path);
                var matcher = LOG_CALL.matcher(source);
                while (matcher.find()) {
                    statements.add(new LogStatement(path, matcher.group()));
                }
            }
        }
        return List.copyOf(statements);
    }

    private String readSources(Path root) throws Exception {
        StringBuilder source = new StringBuilder();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(value -> value.toString().endsWith(".java")).sorted().toList()) {
                source.append(Files.readString(path)).append('\n');
            }
        }
        return source.toString();
    }

    private record LogStatement(Path path, String source) {
    }
}
