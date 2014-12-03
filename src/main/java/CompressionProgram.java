import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * @author igor.kostromin
 *         19.05.2014 14:23
 */
public class CompressionProgram {
    public static void main(String[] args) throws IOException {
        ArithmeticCoder64 coder = new ArithmeticCoder64( 4, 64 );

        int[] message = {
                1,0,0,0,0,0,0,0,0,0,0,0,1,2,3
        };
        coder.count( message );
//        coder.initTest();

        ByteArrayOutputStream encoded = coder.encode( message );
        for ( byte b : encoded.toByteArray() ) {
            System.out.println(Integer.toBinaryString( b & 0xFF ));
        }

        int[] decoded = coder.decode( new ByteArrayInputStream( encoded.toByteArray() ), message.length );
        for (int i = 0; i < message.length; i++){
            if ( message[i] != decoded[i] ) {
                System.out.println("error");
            }
        }

        //byte[] bytes = Files.readAllBytes( Paths.get( "d:\\all\\compression\\algorithms\\src\\main\\resources\\book1.txt" ) );
//        byte[] bytes = Files.readAllBytes( Paths.get( "d:\\elwood\\work\\dzru\\trie\\src\\main\\resources\\book1.txt" ) );
//        int[] message = new int[bytes.length];
//        for(int i=0; i < bytes.length;i++)
//            message[i]=bytes[i] & 0xFF;
//        ArithmeticCoder64 coder = new ArithmeticCoder64( 256, 64 );
//        coder.count( message );
//
//        ByteArrayOutputStream encoded = coder.encode( message );
//        byte[] encodedBytes = encoded.toByteArray();
//
//        System.out.println(String.format("Source size %d encoded size %d ratio %f%%",
//                message.length, encodedBytes.length, encodedBytes.length * 100.0 / message.length));
//
//        int[] decoded = coder.decode( new ByteArrayInputStream( encodedBytes ), message.length );
//
//        for (int i = 0; i < bytes.length; i++){
//            if ( decoded[i] != message[i] ) {
//                System.out.println("error");
//            }
//        }
    }
}
