import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Iterator;
import java.util.Locale;

public class JsonNumberToStringConverter {

    // Türkçe format (virgül ondalık ayraç, nokta binlik ayraç)
    private static final DecimalFormat decimalFormat;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("tr", "TR"));
        symbols.setDecimalSeparator(',');
        symbols.setGroupingSeparator('.');
        decimalFormat = new DecimalFormat("###,##0.00", symbols);
    }

    public static String convertAllNumbersToFormattedStrings(String originalJson) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(originalJson);
        JsonNode processed = convertNumbersToFormattedStrings(root);
        return mapper.writeValueAsString(processed);
    }

    private static JsonNode convertNumbersToFormattedStrings(JsonNode node) {
        if (node.isObject()) {
            ObjectNode newObject = JsonNodeFactory.instance.objectNode();
            Iterator<String> fieldNames = node.fieldNames();

            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                JsonNode child = node.get(fieldName);
                newObject.set(fieldName, convertNumbersToFormattedStrings(child));
            }

            return newObject;

        } else if (node.isArray()) {
            ArrayNode newArray = JsonNodeFactory.instance.arrayNode();
            for (JsonNode item : node) {
                newArray.add(convertNumbersToFormattedStrings(item));
            }
            return newArray;

        } else if (node.isNumber()) {
            if (node.isFloatingPointNumber()) {
                // Ondalıklı sayılar formatlanarak String'e çevrilir
                return new TextNode(decimalFormat.format(node.asDouble()));
            } else {
                // Tam sayılar direkt String'e çevrilir (format yok)
                return new TextNode(node.asText());
            }

        } else {
            return node; // Diğer tipler olduğu gibi bırakılır
        }
    }
}
