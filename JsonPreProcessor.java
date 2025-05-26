package com.ykl.leasing.ngla.system.common.util;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class JsonPreProcessor {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final DateTimeFormatter defaultDateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter defaultDateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DecimalFormat decimalFormatter = new DecimalFormat("#,##0.00",
            DecimalFormatSymbols.getInstance(new Locale("tr", "TR")));

    public static String enrichDatesAndNumbersWithFormattedStrings(String jsonString, Map<String, String> customFormatters) {
        try {
            JsonNode rootNode = mapper.readTree(jsonString);
            JsonNode enrichedNode = enrichNode(rootNode, "", customFormatters);
            return mapper.writeValueAsString(enrichedNode);
        } catch (Exception e) {
            throw new RuntimeException("JSON işlenirken hata oluştu: " + e.getMessage(), e);
        }
    }

    private static JsonNode enrichNode(JsonNode node, String path, Map<String, String> customFormatters) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            List<String> keys = new ArrayList<>();
            object.fieldNames().forEachRemaining(keys::add);

            for (String key : keys) {
                JsonNode value = object.get(key);
                String fullPath = path.isEmpty() ? key : path + "." + key;

                JsonNode processedValue = enrichNode(value, fullPath, customFormatters);
                object.set(key, processedValue);

                Optional<Map.Entry<String, String>> matchedFormatter = getMatchingFormatter(fullPath, customFormatters);
                if (matchedFormatter.isPresent()) {
                    String formatterValue = matchedFormatter.get().getValue();
                    String[] parts = formatterValue.split("\\|", 3);

                    if (parts.length >= 2) {
                        String type = parts[0].trim();
                        String pattern = parts[1].trim();
                        Locale locale = parts.length == 3 ? parseLocale(parts[2].trim()) : new Locale("tr", "TR");

                        switch (type) {
                            case "DATE":
                                LocalDate parsedDate = tryParseDateNode(value);
                                if (parsedDate != null) {
                                    DateTimeFormatter df = DateTimeFormatter.ofPattern(pattern, locale);
                                    object.put(key + "Formatted", parsedDate.format(df));
                                }
                                break;
                            case "DATETIME":
                                LocalDateTime parsedDateTime = tryParseDateTimeNode(value);
                                if (parsedDateTime != null) {
                                    DateTimeFormatter dtf = DateTimeFormatter.ofPattern(pattern, locale);
                                    object.put(key + "Formatted", parsedDateTime.format(dtf));
                                }
                                break;
                            case "DECIMAL":
                                if (value.isNumber()) {
                                    DecimalFormat df = new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(locale));
                                    object.put(key + "Formatted", df.format(value.numberValue()));
                                } else if (value.isTextual()) {
                                    if (isParsableToDecimal(value.asText()) && isFloatingPoint(value.asText())) {
                                        BigDecimal val = new BigDecimal(value.asText());
                                        DecimalFormat df = new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(locale));
                                        object.put(key + "Formatted", df.format(val));
                                    }
                                }
                                break;
                        }
                    } else {
                        // Default format fallback
                        if (value.isNumber()) {
                            object.put(key + "Formatted", decimalFormatter.format(value.numberValue()));
                        } else if (value.isTextual() && isParsableToDecimal(value.asText()) && isFloatingPoint(value.asText())) {
                            object.put(key + "Formatted", decimalFormatter.format(new BigDecimal(value.asText())));
                        } else if (value.isArray() && isLikelyDateArray(value)) {
                            String formattedDate = tryParseArrayDate(value);
                            if (formattedDate != null) {
                                object.put(key + "Formatted", formattedDate);
                            }
                        }
                    }
                } else {
                    // Default format fallback if value is numeric and floating point
                    if (value.isFloatingPointNumber()) {
                        object.put(key + "Formatted", decimalFormatter.format(value.doubleValue()));
                    } else if (value.isArray() && isLikelyDateArray(value)) {
                        String formattedDate = tryParseArrayDate(value);
                        if (formattedDate != null) {
                            object.put(key + "Formatted", formattedDate);
                        }
                    }
                }
            }
        } else if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            for (int i = 0; i < array.size(); i++) {
                String indexedPath = path + ".*";
                array.set(i, enrichNode(array.get(i), indexedPath, customFormatters));
            }
        }
        return node;
    }

    private static Optional<Map.Entry<String, String>> getMatchingFormatter(String path, Map<String, String> formatters) {
        for (Map.Entry<String, String> entry : formatters.entrySet()) {
            String key = entry.getKey().replace(".", "\\.").replace("*", ".*");
            if (Pattern.matches(key, path)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
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

    private static LocalDate tryParseDateNode(JsonNode value) {
        if (value.isTextual()) {
            String[] patterns = {
                    "yyyy-MM-dd'T'HH:mm:ss",
                    "yyyy-MM-dd HH:mm:ss",
                    "yyyy-MM-dd",
                    "dd.MM.yyyy",
                    "dd.MM.yyyy HH:mm"
            };
            for (String pattern : patterns) {
                try {
                    return LocalDate.parse(value.asText(), DateTimeFormatter.ofPattern(pattern));
                } catch (Exception ignored) {
                }
            }
        } else if (value.isArray() && isLikelyDateArray(value)) {
            try {
                return LocalDate.of(value.get(0).asInt(), value.get(1).asInt(), value.get(2).asInt());
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static LocalDateTime tryParseDateTimeNode(JsonNode value) {
        if (value.isTextual()) {
            String[] patterns = {
                    "yyyy-MM-dd'T'HH:mm:ss",
                    "yyyy-MM-dd HH:mm:ss",
                    "yyyy-MM-dd HH:mm",
                    "dd.MM.yyyy HH:mm"
            };
            for (String pattern : patterns) {
                try {
                    return LocalDateTime.parse(value.asText(), DateTimeFormatter.ofPattern(pattern));
                } catch (Exception ignored) {
                }
            }
        } else if (value.isArray() && isLikelyDateArray(value)) {
            try {
                int year = value.get(0).asInt();
                int month = value.get(1).asInt();
                int day = value.get(2).asInt();
                int hour = value.size() > 3 ? value.get(3).asInt() : 0;
                int minute = value.size() > 4 ? value.get(4).asInt() : 0;
                return LocalDateTime.of(year, month, day, hour, minute);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static Locale parseLocale(String localeStr) {
        String[] parts = localeStr.split("-");
        return parts.length == 2 ? new Locale(parts[0], parts[1]) : new Locale(localeStr);
    }

    private static boolean isParsableToDecimal(String text) {
        try {
            new BigDecimal(text);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isFloatingPoint(String text) {
        return text.contains(".") || text.contains(",");
    }
}
