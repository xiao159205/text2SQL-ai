package com.xym.antifraud.query;

import org.springframework.stereotype.Component;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.temporal.TemporalAccessor;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** 将命名参数展开为便于阅读的 SQL，不参与数据库执行。 */
@Component
public class SqlLiteralRenderer {

    private static final Pattern NAMED_PARAMETER =
            Pattern.compile(":([A-Za-z][A-Za-z0-9_]*)");

    public String render(String parameterizedSql, Map<String, Object> parameters) {
        Matcher matcher = NAMED_PARAMETER.matcher(parameterizedSql);
        StringBuilder rendered = new StringBuilder(parameterizedSql.length() + 64);
        while (matcher.find()) {
            String parameterName = matcher.group(1);
            if (!parameters.containsKey(parameterName)) {
                throw new IllegalArgumentException("SQL参数不存在: " + parameterName);
            }
            matcher.appendReplacement(rendered,
                    Matcher.quoteReplacement(toSqlLiteral(parameters.get(parameterName))));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    private String toSqlLiteral(Object value) {
        if (value == null) return "NULL";
        if (value instanceof Collection<?> values) {
            return values.stream().map(this::toSqlLiteral).collect(Collectors.joining(", "));
        }
        if (value.getClass().isArray()) {
            return IntStream.range(0, Array.getLength(value))
                    .mapToObj(index -> toSqlLiteral(Array.get(value, index)))
                    .collect(Collectors.joining(", "));
        }
        if (value instanceof BigDecimal decimal) return decimal.toPlainString();
        if (value instanceof BigInteger || value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long) return value.toString();
        if (value instanceof Float number) {
            if (!Float.isFinite(number)) throw new IllegalArgumentException("浮点SQL参数必须是有限数值");
            return number.toString();
        }
        if (value instanceof Double number) {
            if (!Double.isFinite(number)) throw new IllegalArgumentException("浮点SQL参数必须是有限数值");
            return number.toString();
        }
        if (value instanceof Boolean bool) return bool ? "TRUE" : "FALSE";
        if (value instanceof byte[] bytes) return "X'" + java.util.HexFormat.of().formatHex(bytes) + "'";
        if (value instanceof TemporalAccessor) return quote(value.toString());
        return quote(value.toString());
    }

    private String quote(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'";
    }
}
