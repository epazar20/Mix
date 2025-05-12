import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Base64;

public class Base64Encoder {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Kullanım: java Base64Encoder <dosya_yolu>");
            return;
        }

        String filePath = args[0];
        try {
            File file = new File(filePath);
            FileInputStream fileInputStream = new FileInputStream(file);
            byte[] fileBytes = new byte[(int) file.length()];
            fileInputStream.read(fileBytes);
            fileInputStream.close();

            String base64 = Base64.getEncoder().encodeToString(fileBytes);
            System.out.println("Base64 çıktısı:");
            System.out.println(base64);
        } catch (IOException e) {
            System.out.println("Hata: " + e.getMessage());
        }
    }
}
