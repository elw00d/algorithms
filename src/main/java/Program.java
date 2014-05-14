import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * @author igor.kostromin
 *         13.05.2014 16:26
 */
public class Program {
    public static void main(String[] args) throws IOException {
        Map<String, Integer> hashMap = new HashMap<String, Integer>(  );
        RadixTrie<Integer> trie = new RadixTrie<Integer>();
        InputStream in = Program.class.getClassLoader().getResourceAsStream( "book1.txt" );
        BufferedReader reader = new BufferedReader( new InputStreamReader( in, "cp1251" ) );
        String s;
        while( (s = reader.readLine()) != null ) {
            String[] words = s.split( " " );
            for ( String word : words ) {
                if (!word.isEmpty()){
                    Integer count = hashMap.get( word );
                    if (null == count){
                        hashMap.put( word, 1 );
                    } else {
                        hashMap.put( word, count + 1 );
//                        hashMap.remove( word );
                    }

                    Integer cnt = ( Integer ) trie.get( word );
                    if (null == cnt){
                        trie.put( word, 1 );
                    } else {
                        trie.put( word, cnt + 1 );
//                        trie.remove( word );
                    }
                }
            }
        }
        System.out.println("Gathered");

        for ( Map.Entry<String, Integer> entry : hashMap.entrySet() ) {
            if (((int)(Integer) trie.get( entry.getKey() )) != entry.getValue()) {
                System.out.println("Error");
            }
        }

        {
            long begin = System.nanoTime();
            for ( int i = 0; i < 100; i++ ) {
                for ( Map.Entry<String, Integer> entry : hashMap.entrySet() ) {
                    hashMap.get( entry.getKey() );
                }
            }
            long end = System.nanoTime();
            System.out.println( String.format( "Hashmap: %d", end-begin ) );
        }

        {
            long begin = System.nanoTime();
            for ( int i = 0; i < 100; i++ ) {
                for ( Map.Entry<String, Integer> entry : hashMap.entrySet() ) {
                    trie.get( entry.getKey() );
                }
            }
            long end = System.nanoTime();
            System.out.println( String.format( "Trie: %d", end-begin ) );
        }


        System.out.println("Finished");
        System.in.read();

        trie.get( "" );
        hashMap.get( "" );
    }
}
