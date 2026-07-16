package top.egon.mario.investment.research.report;

import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;

import java.util.regex.Pattern;

/**
 * Rejects raw HTML so clients can render Markdown without enabling an HTML parser.
 */
public final class ResearchMarkdownPolicy {

    private static final Pattern RAW_HTML = Pattern.compile(
            "(?is)<!--.*?-->|</?[a-z][^>]*>|<![a-z][^>]*>|<\\?[a-z][^>]*\\?>");

    private ResearchMarkdownPolicy() {
    }

    public static void requireSafe(String markdown) {
        if (markdown == null || RAW_HTML.matcher(markdown).find()) {
            throw new InvestmentException(InvestmentErrorCode.INVALID_REQUEST,
                    "Research report Markdown must not contain raw HTML");
        }
    }
}
