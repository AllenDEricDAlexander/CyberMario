package top.egon.mario.rbac.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.util.pattern.PathPatternParser;
import top.egon.mario.common.utils.LogUtil;
import top.egon.mario.rbac.service.model.ApiPermissionRule;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Matches an HTTP method and request path to the most specific configured API rule.
 */
@Component
@Slf4j
public class ApiRuleMatcher {

    private final AntPathMatcher antPathMatcher = new AntPathMatcher();
    private final PathPatternParser pathPatternParser = new PathPatternParser();

    public Optional<ApiPermissionRule> match(String httpMethod, String requestPath, List<ApiPermissionRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return Optional.empty();
        }
        Optional<ApiPermissionRule> matchedRule = rules.stream()
                .filter(rule -> methodMatches(httpMethod, rule.httpMethod()))
                .filter(rule -> pathMatches(requestPath, rule))
                .min(Comparator
                        .comparingInt((ApiPermissionRule rule) -> methodRank(httpMethod, rule.httpMethod()))
                        .thenComparingInt(rule -> matcherRank(rule.matcherType()))
                        .thenComparing((ApiPermissionRule rule) -> rule.urlPattern().length(), Comparator.reverseOrder()));
        LogUtil.debug(log).log("rbac api rule matched, method={}, path={}, matched={}",
                httpMethod, requestPath, matchedRule.isPresent());
        return matchedRule;
    }

    private boolean methodMatches(String requestMethod, String ruleMethod) {
        return "ANY".equalsIgnoreCase(ruleMethod) || requestMethod.equalsIgnoreCase(ruleMethod);
    }

    private int methodRank(String requestMethod, String ruleMethod) {
        return requestMethod.equalsIgnoreCase(ruleMethod) ? 0 : 1;
    }

    private boolean pathMatches(String requestPath, ApiPermissionRule rule) {
        return switch (rule.matcherType()) {
            case EXACT -> requestPath.equals(rule.urlPattern());
            case MVC -> pathPatternParser.parse(rule.urlPattern()).matches(PathContainer.parsePath(requestPath));
            case ANT -> antPathMatcher.match(rule.urlPattern(), requestPath);
            case REGEX -> Pattern.matches(rule.urlPattern(), requestPath);
        };
    }

    private int matcherRank(top.egon.mario.rbac.po.enums.ApiMatcherType matcherType) {
        return switch (matcherType) {
            case EXACT -> 0;
            case MVC -> 1;
            case ANT -> 2;
            case REGEX -> 3;
        };
    }

}
