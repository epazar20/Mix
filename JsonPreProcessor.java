import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class JsonConverter {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DecimalFormat decimalFormatter = new DecimalFormat("#,##0.00");

    public static String enrichDatesAndNumbersWithFormattedStrings(String jsonString) {
        try {
            JsonNode rootNode = mapper.readTree(jsonString);
            JsonNode enrichedNode = enrichNode(rootNode);
            return mapper.writeValueAsString(enrichedNode);
        } catch (Exception e) {
            throw new RuntimeException("JSON işlenirken hata oluştu: " + e.getMessage(), e);
        }
    }

    private static JsonNode enrichNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            List<String> keys = new ArrayList<>();
            fields.forEachRemaining(e -> keys.add(e.getKey()));

            for (String key : keys) {
                JsonNode value = object.get(key);
                JsonNode processedValue = enrichNode(value);
                object.set(key, processedValue);

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
                array.set(i, enrichNode(array.get(i)));
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
