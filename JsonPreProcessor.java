package com.ykl.leasing.ngla.system.common.util;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class JsonPreProcessor {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DecimalFormat decimalFormatter = new DecimalFormat("#,##0.00");

    public static String enrichDatesAndNumbersWithFormattedStrings(String jsonString) {
        return enrichDatesAndNumbersWithFormattedStrings(jsonString, new HashMap<>());
    }

    public static String enrichDatesAndNumbersWithFormattedStrings(String jsonString, Map<String, String> customFormatters) {
        try {
            JsonNode rootNode = mapper.readTree(jsonString);
            JsonNode enrichedNode = enrichNode(rootNode, customFormatters);
            return mapper.writeValueAsString(enrichedNode);
        } catch (Exception e) {
            throw new RuntimeException("JSON işlenirken hata oluştu: " + e.getMessage(), e);
        }
    }

    private static JsonNode enrichNode(JsonNode node, Map<String, String> customFormatters) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            List<String> keys = new ArrayList<>();
            fields.forEachRemaining(e -> keys.add(e.getKey()));

            for (String key : keys) {
                JsonNode value = object.get(key);
                JsonNode processedValue = enrichNode(value, customFormatters);
                object.set(key, processedValue);

                String customPattern = customFormatters.get(key);
                if (customPattern != null && value.isTextual()) {
                    String text = value.asText();
                    String formatted = tryCustomFormat(text, customPattern);
                    if (formatted != null) {
                        object.put(key + "Formatted", formatted);
                        continue;
                    }
                }

                if (value.isTextual()) {
                    String text = value.asText();
                    String formattedDate = tryFormatDate(text);
                    if (formattedDate != null) {
                        object.put(key + "Formatted", formattedDate);
                        continue;
                    }

                    String formattedNumber = tryFormatDecimal(text);
                    if (formattedNumber != null) {
                        object.put(key + "Formatted", formattedNumber);
                    }
                }

                if (value.isArray() && isLikelyDateArray(value)) {
                    String formattedDate = tryParseArrayDate(value);
                    if (formattedDate != null) {
                        object.put(key + "Formatted", formattedDate);
                    }
                }

                if (value.isFloatingPointNumber()) {
                    object.put(key + "Formatted", decimalFormatter.format(value.doubleValue()));
                }
            }
        } else if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            for (int i = 0; i < array.size(); i++) {
                array.set(i, enrichNode(array.get(i), customFormatters));
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
                return dt.format(dateTimeFormatter);
            } else {
                LocalDate dt = LocalDate.of(year, month, day);
                return dt.format(dateFormatter);
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
                    return dt.format(dateTimeFormatter);
                }
                return dt.toLocalDate().format(dateFormatter);
            } catch (Exception ignored) {}
            try {
                LocalDate d = LocalDate.parse(value, DateTimeFormatter.ofPattern(pattern));
                return d.format(dateFormatter);
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

    private static String tryCustomFormat(String value, String pattern) {
        // Önce tarih mi sayı mı olduğunu anlamaya çalışalım
        try {
            LocalDateTime dt = LocalDateTime.parse(value);
            return dt.format(DateTimeFormatter.ofPattern(pattern));
        } catch (Exception ignored) {}
        try {
            LocalDate d = LocalDate.parse(value);
            return d.format(DateTimeFormatter.ofPattern(pattern));
        } catch (Exception ignored) {}

        try {
            BigDecimal val = new BigDecimal(value);
            DecimalFormat customDecimalFormatter = new DecimalFormat(pattern);
            return customDecimalFormatter.format(val);
        } catch (Exception ignored) {}

        return null;
    }

    private static String tryCustomFormat(String value, String typeAndPattern) {
    if (!typeAndPattern.contains("|")) return null;

    String[] parts = typeAndPattern.split("\\|", 2);
    if (parts.length != 2) return null;

    String type = parts[0].toUpperCase(Locale.ROOT);
    String pattern = parts[1];

    try {
        switch (type) {
            case "DATE":
                try {
                    LocalDateTime dt = LocalDateTime.parse(value);
                    return dt.format(DateTimeFormatter.ofPattern(pattern));
                } catch (Exception e) {
                    LocalDate d = LocalDate.parse(value);
                    return d.format(DateTimeFormatter.ofPattern(pattern));
                }

            case "DECIMAL":
                BigDecimal val = new BigDecimal(value);
                DecimalFormat customDecimalFormatter = new DecimalFormat(pattern);
                return customDecimalFormatter.format(val);

            default:
                return null;
        }
    } catch (Exception e) {
        return null;
    }
}
}
