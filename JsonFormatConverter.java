import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;

public class JsonFormatConverter {

    // Tarih ve TarihSaat formatları
    private static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public static String convertAllToFormattedStrings(String json) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);
        JsonNode result = convertNumbersAndDates(root);
        return mapper.writeValueAsString(result);
    }

    private static JsonNode convertNumbersAndDates(JsonNode node) {
        if (node.isObject()) {
            ObjectNode newObj = JsonNodeFactory.instance.objectNode();
            Iterator<String> fields = node.fieldNames();
            while (fields.hasNext()) {
                String field = fields.next();
                newObj.set(field, convertNumbersAndDates(node.get(field)));
            }
            return newObj;

        } else if (node.isArray()) {
            ArrayNode newArr = JsonNodeFactory.instance.arrayNode();
            for (JsonNode item : node) {
                newArr.add(convertNumbersAndDates(item));
            }
            return newArr;

        } else if (node.isTextual()) {
            return node;
        } else if (node.isNumber()) {
            // Sayılar (Double, Long, BigDecimal vb.) için format uygula
            if (node.isDouble() || node.isFloat() || node.isBigDecimal()) {
                return new TextNode(formatNumber(node.asText()));
            }
            return node;

        } else if (node.isObject() || node.isArray()) {
            // Tarihler için dönüştürme işlemi yap
            if (node.size() == 3 && node.get(0).isInt() && node.get(1).isInt() && node.get(2).isInt()) {
                try {
                    LocalDate date = LocalDate.of(node.get(0).asInt(), node.get(1).asInt(), node.get(2).asInt());
                    return new TextNode(date.format(dateFormatter));
                } catch (Exception e) {
                    return node;
                }
            }

            if (node.size() == 5 &&
                node.get(0).isInt() && node.get(1).isInt() && node.get(2).isInt() &&
                node.get(3).isInt() && node.get(4).isInt()) {
                try {
                    LocalDateTime dateTime = LocalDateTime.of(
                        node.get(0).asInt(), node.get(1).asInt(), node.get(2).asInt(),
                        node.get(3).asInt(), node.get(4).asInt());
                    return new TextNode(dateTime.format(dateTimeFormatter));
                } catch (Exception e) {
                    return node;
                }
            }
        }
        return node;
    }

    // Sayısal format (TL formatında örneğin)
    private static String formatNumber(String number) {
        try {
            BigDecimal value = new BigDecimal(number);
            return value.setScale(2, BigDecimal.ROUND_HALF_UP).toString() + " TL";
        } catch (NumberFormatException e) {
            return number; // Hata durumunda orijinal değeri döndür
        }
    }
}
