package top.egon.mario.investment.quant.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvestmentDatasetHasherTests {

    private final InvestmentDatasetHasher hasher = new InvestmentDatasetHasher(new ObjectMapper());

    @Test
    void recursivelySortsObjectKeysWithoutChangingArrayOrder() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("z", List.of(Map.of("b", "2", "a", "1"), "tail"));
        first.put("a", Map.of("d", 4, "c", 3));
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("a", Map.of("c", 3, "d", 4));
        second.put("z", List.of(Map.of("a", "1", "b", "2"), "tail"));

        assertThat(hasher.canonicalJson(first)).isEqualTo(hasher.canonicalJson(second));
        assertThat(hasher.hash(first)).isEqualTo(hasher.hash(second)).hasSize(64);
        assertThat(hasher.hash(List.of("tail", "head")))
                .isNotEqualTo(hasher.hash(List.of("head", "tail")));
    }

    @Test
    void canonicalizesEmbeddedJsonAndRejectsInvalidInput() {
        assertThat(hasher.canonicalizeJson("{\"z\":1,\"a\":{\"d\":4,\"c\":3}}"))
                .isEqualTo("{\"a\":{\"c\":3,\"d\":4},\"z\":1}");
        assertThatThrownBy(() -> hasher.canonicalizeJson("not-json"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
