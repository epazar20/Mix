import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonArrayChecker {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static boolean isJsonArray(String jsonString) {
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonString);
            return jsonNode.isArray();
        } catch (Exception e) {
            return false; // Geçersiz JSON veya parse hatası durumunda false döner
        }
    }

    // Kullanım örneği
    public static void main(String[] args) {
        String jsonArray = "[{\"name\":\"John\"}, {\"name\":\"Jane\"}]";
        String jsonObject = "{\"name\":\"John\", \"age\":30}";
        String invalidJson = "This is not JSON";

        System.out.println("Birinci JSON array mi? " + isJsonArray(jsonArray));   // true
        System.out.println("İkinci JSON array mi? " + isJsonArray(jsonObject));  // false
        System.out.println("Geçersiz JSON array mi? " + isJsonArray(invalidJson)); // false
    }
}
