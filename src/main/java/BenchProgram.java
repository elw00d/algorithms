import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * @author igor.kostromin
 *         03.12.2014 16:36
 */
public class BenchProgram {
    private static int[] getOriginalMessage() {
        byte[] bytes = new byte[0];
        try {
            bytes = Files.readAllBytes(Paths.get("d:\\all\\compression\\algorithms\\src\\main\\resources\\book1.txt"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        int[] message = new int[bytes.length];
        for(int i=0; i < bytes.length;i++)
            message[i]=bytes[i] & 0xFF;
        return message;
    }

    private static void verify(int[] decoded, int[] originalMessage) {
        for (int i = 0; i < originalMessage.length; i++){
            if ( decoded[i] != originalMessage[i] ) {
                throw new RuntimeException("Verification failed");
            }
        }
    }

    public static void main(String[] args) throws IOException {
        int[] message = getOriginalMessage();

        processArithm32(message);

        processArithm32Precise(message);

        processArithm64(message);

        processRange32(message);

        processCarrylessRange32Unoptimized(message);

        for (int minRangeBits = 8; minRangeBits <= CarrylessRangeCoder64.MIN_RANGE_BITS_MAX; minRangeBits++) {
            processCarrylessRange64(message, minRangeBits);
        }
    }

    private static void processRange32(int[] message) {
        RangeCoder coder = new RangeCoder( 256 );
        coder.count( message );

        ByteArrayOutputStream encoded = coder.encode( message );
        byte[] encodedBytes = encoded.toByteArray();

        System.out.println("Range-32-unoptimized");
        System.out.println(String.format("Source size %d encoded size %d ratio %f%%",
                message.length, encodedBytes.length, encodedBytes.length * 100.0 / message.length));

        int[] decoded = coder.decode(new ByteArrayInputStream(encodedBytes), message.length);
        verify(decoded, message);
    }

    private static void processCarrylessRange32Unoptimized(int[] message) {
        CarrylessRangeCoder coder = new CarrylessRangeCoder( 256 );
        coder.count( message );

        ByteArrayOutputStream encoded = coder.encodeUnoptimized( message );
        byte[] encodedBytes = encoded.toByteArray();

        System.out.println("CarrylessRange-32-unoptimized");
        System.out.println(String.format("Source size %d encoded size %d ratio %f%%",
                message.length, encodedBytes.length, encodedBytes.length * 100.0 / message.length));

        int[] decoded = coder.decodeUnoptimized(new ByteArrayInputStream(encodedBytes), message.length);
        verify(decoded, message);
    }

    private static void processCarrylessRange64(int[] message, int minRangeBits) {
        CarrylessRangeCoder64 coder = new CarrylessRangeCoder64( 256, minRangeBits );
        coder.count( message );

        ByteArrayOutputStream encoded = coder.encode( message );
        byte[] encodedBytes = encoded.toByteArray();

        System.out.println("CarrylessRange-64, MIN_RANGE=2^" + minRangeBits);
        System.out.println(String.format("Source size %d encoded size %d ratio %f%%",
                message.length, encodedBytes.length, encodedBytes.length * 100.0 / message.length));

        int[] decoded = coder.decode(new ByteArrayInputStream(encodedBytes), message.length);
        verify(decoded, message);
    }

    private static void processArithm32Precise(int[] message) {
        ArithmeticCoder coder = new ArithmeticCoder(256, 32);
        coder.count(message);

        ByteArrayOutputStream encoded = coder.encode( message, true );
        byte[] encodedBytes = encoded.toByteArray();

        System.out.println("Arithm-32-precise");
        System.out.println(String.format("Source size %d encoded size %d ratio %f%%",
                message.length, encodedBytes.length, encodedBytes.length * 100.0 / message.length));

        int[] decoded = coder.decode(new ByteArrayInputStream(encodedBytes), message.length, true);
        verify(decoded, message);
    }

    private static void processArithm32(int[] message) {
        ArithmeticCoder coder = new ArithmeticCoder(256);
        coder.count(message);

        ByteArrayOutputStream encoded = coder.encode( message, false );
        byte[] encodedBytes = encoded.toByteArray();

        System.out.println("Arithm-32");
        System.out.println(String.format("Source size %d encoded size %d ratio %f%%",
                message.length, encodedBytes.length, encodedBytes.length * 100.0 / message.length));

        int[] decoded = coder.decode(new ByteArrayInputStream(encodedBytes), message.length, false);
        verify(decoded, message);
    }

    private static void processArithm64(int[] message) {
        ArithmeticCoder64 coder = new ArithmeticCoder64(256);
        coder.count(message);

        ByteArrayOutputStream encoded = coder.encode( message );
        byte[] encodedBytes = encoded.toByteArray();

        System.out.println("Arithm-64");
        System.out.println(String.format("Source size %d encoded size %d ratio %f%%",
                message.length, encodedBytes.length, encodedBytes.length * 100.0 / message.length));

        int[] decoded = coder.decode(new ByteArrayInputStream(encodedBytes), message.length);
        verify(decoded, message);
    }
}
