import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class JsonPreProcessor {

    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DecimalFormat decimalFormatter = new DecimalFormat("#,##0.00");

    public static JsonNode enrichDatesAndNumbersWithFormattedStrings(JsonNode node) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            List<String> keys = new ArrayList<>();
            fields.forEachRemaining(e -> keys.add(e.getKey()));

            for (String key : keys) {
                JsonNode value = object.get(key);
                JsonNode processedValue = enrichDatesAndNumbersWithFormattedStrings(value);
                object.set(key, processedValue);

                // Date as text (ISO or other formats)
                if (value.isTextual()) {
                    String text = value.asText();
                    String formatted = tryFormatDate(text);
                    if (formatted != null) {
                        object.put(key + "Formatted", formatted);
                        continue; // Skip number parsing if date matched
                    }

                    // Try parsing string as number
                    String formattedNumber = tryFormatDecimal(text);
                    if (formattedNumber != null) {
                        object.put(key + "Formatted", formattedNumber);
                    }
                }

                // Date as array (e.g. [2024, 5, 10])
                if (value.isArray() && isLikelyDateArray(value)) {
                    String formattedDate = tryParseArrayDate(value);
                    if (formattedDate != null) {
                        object.put(key + "Formatted", formattedDate);
                    }
                }

                // Number (Double, Float, BigDecimal)
                if (value.isFloatingPointNumber()) {
                    object.put(key + "Formatted", decimalFormatter.format(value.doubleValue()));
                }
            }
        } else if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            for (int i = 0; i < array.size(); i++) {
                array.set(i, enrichDatesAndNumbersWithFormattedStrings(array.get(i)));
            }
        }
        return node;
    }

    private static boolean isLikelyDateArray(JsonNode array) {
        int size = array.size();
        return size >= 3 && size <= 6 &&
                array.get(0).isInt() && array.get(1).isInt() && array.get(2).isInt();
    }

    private static String tryParseArrayDate(JsonNode array) {
        try {
            int year = array.get(0).asInt();
            int month = array.get(1).asInt();
            int day = array.get(2).asInt();
            int hour = array.size() > 3 ? array.get(3).asInt() : 0;
            int minute = array.size() > 4 ? array.get(4).asInt() : 0;
            LocalDateTime dt = LocalDateTime.of(year, month, day, hour, minute);
            return dt.format(dateTimeFormatter);
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
                return dt.format(dateTimeFormatter);
            } catch (Exception ignored) {}
            try {
                LocalDate d = LocalDate.parse(value, DateTimeFormatter.ofPattern(pattern));
                return d.atStartOfDay().format(dateTimeFormatter);
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
}
