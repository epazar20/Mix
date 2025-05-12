import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public class Base64Encoder {
    public static void main(String[] args) {
        try {
            // resources klasöründen Template.docx'i oku
            InputStream inputStream = Base64Encoder.class.getClassLoader().getResourceAsStream("Template.docx");

            if (inputStream == null) {
                System.out.println("Template.docx bulunamadı! resources klasöründe olduğundan emin olun.");
                return;
            }

            // InputStream'den byte dizisi oluştur
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[1024];
            int nRead;
            while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }

            buffer.flush();
            byte[] fileBytes = buffer.toByteArray();

            // Base64'e çevir ve ekrana yaz
            String base64 = Base64.getEncoder().encodeToString(fileBytes);
            System.out.println("Base64 çıktısı:");
            System.out.println(base64);

        } catch (IOException e) {
            System.out.println("Hata oluştu: " + e.getMessage());
        }
    }
}
