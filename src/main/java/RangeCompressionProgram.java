import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * @author igor.kostromin
 *         26.05.2014 18:41
 */
public class RangeCompressionProgram {
    public static void main(String[]args) throws IOException {
//        byte[] bytes = Files.readAllBytes(Paths.get("d:\\all\\compression\\algorithms\\src\\main\\resources\\book1.txt"));
        byte[] bytes = Files.readAllBytes(Paths.get("d:\\elwood\\work\\dzru\\trie\\src\\main\\resources\\book1.txt"));
        int[] message = new int[bytes.length];
        for(int i=0; i < bytes.length;i++)
            message[i]=bytes[i] & 0xFF;
        RangeCoder coder = new RangeCoder( 256 );
        coder.count( message );

        ByteArrayOutputStream encoded = coder.encode( message );
        byte[] encodedBytes = encoded.toByteArray();

        System.out.println(String.format("Source size %d encoded size %d ratio %f%%",
                message.length, encodedBytes.length, encodedBytes.length * 100.0 / message.length));

        int[] decoded = coder.decode( new ByteArrayInputStream( encodedBytes ), message.length );

        for (int i = 0; i < bytes.length; i++){
            if ( decoded[i] != message[i] ) {
                System.out.println("error");
            }
        }
    }
}
