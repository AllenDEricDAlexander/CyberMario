package top.egon.mario.investment.common.web;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Strict JSON codec for Investment decimal-string fields.
 */
public final class InvestmentDecimalCodec {

    private static final Pattern CANONICAL_DECIMAL = Pattern.compile("-?(?:0|[1-9]\\d*)(?:\\.\\d+)?");

    private InvestmentDecimalCodec() {
    }

    public static String format(BigDecimal value) {
        return Objects.requireNonNull(value, "value").toPlainString();
    }

    public static BigDecimal parse(String value) {
        if (value == null || !CANONICAL_DECIMAL.matcher(value).matches()) {
            throw new IllegalArgumentException("Investment decimal must use canonical decimal notation");
        }
        return new BigDecimal(value);
    }

    public static final class Serializer extends StdScalarSerializer<BigDecimal> {

        public Serializer() {
            super(BigDecimal.class);
        }

        @Override
        public void serialize(BigDecimal value, JsonGenerator generator, SerializerProvider provider) throws IOException {
            generator.writeString(format(value));
        }
    }

    public static final class Deserializer extends StdScalarDeserializer<BigDecimal> {

        public Deserializer() {
            super(BigDecimal.class);
        }

        @Override
        public BigDecimal deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_STRING)) {
                context.reportInputMismatch(BigDecimal.class,
                        "Investment decimal must be encoded as a JSON string");
            }
            String value = parser.getText();
            try {
                return parse(value);
            } catch (IllegalArgumentException ex) {
                throw context.weirdStringException(value, BigDecimal.class, ex.getMessage());
            }
        }
    }
}
