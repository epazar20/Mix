package com.ykl.leasing.ngla.system.common.util;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class JsonPreProcessor {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final DateTimeFormatter defaultDateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter defaultDateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Locale defaultLocale = new Locale("tr", "TR");
    private static final DecimalFormat decimalFormatter = new DecimalFormat("#,##0.00", new DecimalFormatSymbols(defaultLocale));

    public static String enrichDatesAndNumbersWithFormattedStrings(String jsonString, Map<String, String> customFormatters) {
        try {
            JsonNode rootNode = mapper.readTree(jsonString);
            JsonNode enrichedNode = enrichNode(rootNode, "", customFormatters);
            return mapper.writeValueAsString(enrichedNode);
        } catch (Exception e) {
            throw new RuntimeException("JSON i\u015flenirken hata olu\u015ftu: " + e.getMessage(), e);
        }
    }

    private static JsonNode enrichNode(JsonNode node, String currentPath, Map<String, String> customFormatters) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            List<String> keys = new ArrayList<>();
            fields.forEachRemaining(e -> keys.add(e.getKey()));

            for (String key : keys) {
                JsonNode value = object.get(key);
                String nextPath = currentPath.isEmpty() ? key : currentPath + "." + key;

                JsonNode processedValue = enrichNode(value, nextPath, customFormatters);
                object.set(key, processedValue);

                if (value.isTextual()) {
                    String text = value.asText();

                    String formatted = tryFormatCustomDate(nextPath, text, customFormatters);
                    if (formatted == null) {
                        formatted = tryFormatDate(text);
                    }
                    if (formatted != null) {
                        object.put(key + "Formatted", formatted);
                        continue;
                    }

                    formatted = tryFormatCustomDecimal(nextPath, text, customFormatters);
                    if (formatted == null) {
                        formatted = tryFormatDecimal(text);
                    }
                    if (formatted != null) {
                        object.put(key + "Formatted", formatted);
                    }
                }

                if (value.isArray() && isLikelyDateArray(value)) {
                    String formatted = tryParseArrayDate(value);
                    if (formatted != null) {
                        object.put(key + "Formatted", formatted);
                    }
                }

                if (value.isNumber()) {
                    boolean isFloatingPoint = value.isFloatingPointNumber();
                    boolean isIntegerLike = value.isInt() || value.isLong() || value.isBigInteger();

                    if (isFloatingPoint) {
                        object.put(key + "Formatted", decimalFormatter.format(value.asDouble()));
                    } else if (isIntegerLike && hasMatchingDecimalFormatterForPath(nextPath, customFormatters)) {
                        DecimalFormat formatter = getDecimalFormatterForPath(nextPath, customFormatters);
                        BigDecimal number = new BigDecimal(value.asText());
                        object.put(key + "Formatted", formatter.format(number));
                    }
                }
            }
        } else if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            for (int i = 0; i < array.size(); i++) {
                String nextPath = currentPath + "[" + i + "]";
                array.set(i, enrichNode(array.get(i), nextPath, customFormatters));
            }
        }
        return node;
    }

    private static boolean isLikelyDateArray(JsonNode array) {
        return array.size() >= 3 && array.size() <= 6 &&
                array.get(0).isInt() && array.get(1).isInt() && array.get(2).isInt();
    }

    private static String tryParseArrayDate(JsonNode array) {
        try {
            int year = array.get(0).asInt();
            int month = array.get(1).asInt();
            int day = array.get(2).asInt();
            int hour = array.size() > 3 ? array.get(3).asInt() : 0;
            int minute = array.size() > 4 ? array.get(4).asInt() : 0;
            if (hour > 0 || minute > 0) {
                LocalDateTime dt = LocalDateTime.of(year, month, day, hour, minute);
                return dt.format(defaultDateTimeFormatter);
            } else {
                LocalDate dt = LocalDate.of(year, month, day);
                return dt.format(defaultDateFormatter);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static String tryFormatDate(String value) {
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd",
                "dd.MM.yyyy",
                "dd.MM.yyyy HH:mm"
        };
        for (String pattern : patterns) {
            try {
                LocalDateTime dt = LocalDateTime.parse(value, DateTimeFormatter.ofPattern(pattern));
                if (dt.getHour() > 0 || dt.getMinute() > 0) {
                    return dt.format(defaultDateTimeFormatter);
                }
                return dt.toLocalDate().format(defaultDateFormatter);
            } catch (Exception ignored) {}
            try {
                LocalDate d = LocalDate.parse(value, DateTimeFormatter.ofPattern(pattern));
                return d.format(defaultDateFormatter);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static String tryFormatDecimal(String text) {
        try {
            BigDecimal val = new BigDecimal(text);
            return decimalFormatter.format(val);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String tryFormatCustomDate(String path, String value, Map<String, String> customFormatters) {
        for (Map.Entry<String, String> entry : customFormatters.entrySet()) {
            String key = entry.getKey();
            String formatStr = entry.getValue();
            if (pathMatches(key, path) && formatStr.toUpperCase().startsWith("DATE|")) {
                String[] parts = formatStr.split("\\|");
                String pattern = parts.length > 1 ? parts[1] : "dd.MM.yyyy";
                Locale locale = parts.length > 2 ? Locale.forLanguageTag(parts[2]) : defaultLocale;
                try {
                    DateTimeFormatter dtf = DateTimeFormatter.ofPattern(pattern, locale);
                    try {
                        LocalDateTime dt = LocalDateTime.parse(value, dtf);
                        return dt.format(dtf);
                    } catch (Exception e) {
                        LocalDate d = LocalDate.parse(value, dtf);
                        return d.format(dtf);
                    }
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private static String tryFormatCustomDecimal(String path, String value, Map<String, String> customFormatters) {
        for (Map.Entry<String, String> entry : customFormatters.entrySet()) {
            String key = entry.getKey();
            String formatStr = entry.getValue();
            if (pathMatches(key, path) && formatStr.toUpperCase().startsWith("DECIMAL|")) {
                String[] parts = formatStr.split("\\|");
                String pattern = parts.length > 1 ? parts[1] : "#,##0.00";
                Locale locale = parts.length > 2 ? Locale.forLanguageTag(parts[2]) : defaultLocale;
                try {
                    DecimalFormatSymbols symbols = new DecimalFormatSymbols(locale);
                    DecimalFormat formatter = new DecimalFormat(pattern, symbols);
                    BigDecimal val = new BigDecimal(value);
                    return formatter.format(val);
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private static boolean hasMatchingDecimalFormatterForPath(String path, Map<String, String> customFormatters) {
        for (Map.Entry<String, String> entry : customFormatters.entrySet()) {
            String key = entry.getKey();
            String formatStr = entry.getValue();
            if (pathMatches(key, path) && formatStr.toUpperCase().startsWith("DECIMAL|")) {
                return true;
            }
        }
        return false;
    }

    private static DecimalFormat getDecimalFormatterForPath(String path, Map<String, String> customFormatters) {
        for (Map.Entry<String, String> entry : customFormatters.entrySet()) {
            String key = entry.getKey();
            String formatStr = entry.getValue();
            if (pathMatches(key, path) && formatStr.toUpperCase().startsWith("DECIMAL|")) {
                String[] parts = formatStr.split("\\|");
                String pattern = parts.length > 1 ? parts[1] : "#,##0.00";
                Locale locale = parts.length > 2 ? Locale.forLanguageTag(parts[2]) : defaultLocale;
                DecimalFormatSymbols symbols = new DecimalFormatSymbols(locale);
                return new DecimalFormat(pattern, symbols);
            }
        }
        return null;
    }

    private static boolean pathMatches(String pattern, String path) {
        if (pattern.equals(path)) return true;
        String regex = "^" + pattern.replace(".", "\\.").replace("*", "[^.]+") + "$";
        return path.matches(regex);
    }

}
