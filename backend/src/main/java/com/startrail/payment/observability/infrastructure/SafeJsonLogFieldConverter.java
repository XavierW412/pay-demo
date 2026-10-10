package com.startrail.payment.observability.infrastructure;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.Locale;

/** 只允许固定日志字段，并将每个值编码为安全的 JSON 字符串内容。 */
public class SafeJsonLogFieldConverter extends ClassicConverter {
    @Override
    public String convert(ILoggingEvent event) {
        String field = getFirstOption();
        if (field == null) {
            return "";
        }
        String value = switch (field) {
            case "service" -> event.getLoggerContextVO().getPropertyMap().get("serviceName");
            case "environment" -> event.getLoggerContextVO().getPropertyMap().get("environment");
            case "requestId" -> event.getMDCPropertyMap().get("requestId");
            case "traceId" -> traceId(event.getMDCPropertyMap().get("traceId"));
            case "routeTemplate" -> event.getMDCPropertyMap().get("routeTemplate");
            case "httpStatus" -> event.getMDCPropertyMap().get("httpStatus");
            case "exceptionClass" -> event.getMDCPropertyMap().get("exceptionClass");
            case "stackFrames" -> event.getMDCPropertyMap().get("stackFrames");
            default -> null;
        };
        if (value == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder();
        int limit = Math.min(value.length(), 512);
        for (int index = 0; index < limit; index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20 || Character.isSurrogate(character)) {
                        escaped.append("\\u");
                        for (int shift = 12; shift >= 0; shift -= 4) {
                            escaped.append(Character.forDigit((character >> shift) & 0xf, 16));
                        }
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private String traceId(String value) {
        if (value == null || !value.matches("[0-9a-fA-F]{32}")) {
            return null;
        }
        return value.toLowerCase(Locale.ROOT);
    }
}
