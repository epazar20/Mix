import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;

public class JsonDateToStringConverter {

    // Tarih ve TarihSaat formatları
    private static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public static String convertDatesToFormattedStrings(String json) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);
        JsonNode result = convertDates(root);
        return mapper.writeValueAsString(result);
    }

    private static JsonNode convertDates(JsonNode node) {
        if (node.isObject()) {
            ObjectNode newObj = JsonNodeFactory.instance.objectNode();
            Iterator<String> fields = node.fieldNames();
            while (fields.hasNext()) {
                String field = fields.next();
                newObj.set(field, convertDates(node.get(field)));
            }
            return newObj;

        } else if (node.isArray()) {
            // Eğer tarih dizi [yyyy,MM,dd] şeklindeyse
            if (node.size() == 3 && node.get(0).isInt() && node.get(1).isInt() && node.get(2).isInt()) {
                try {
                    LocalDate date = LocalDate.of(node.get(0).asInt(), node.get(1).asInt(), node.get(2).asInt());
                    return new TextNode(date.format(dateFormatter));
                } catch (Exception e) {
                    return node; // Format uymazsa orijinali döndür
                }
            }

            // Eğer tarih-saat dizi [yyyy,MM,dd,HH,mm] şeklindeyse
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

            // Diğer array öğeleri için döngü
            ArrayNode newArr = JsonNodeFactory.instance.arrayNode();
            for (JsonNode item : node) {
                newArr.add(convertDates(item));
            }
            return newArr;

        } else {
            return node;
        }
    }
}
