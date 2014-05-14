import org.junit.Assert;
import org.junit.Test;

/**
 * @author igor.kostromin
 *         13.05.2014 18:03
 */

public class Tests {
    @Test
    public void test() {
        RadixTrie<Integer> trie = new RadixTrie<Integer>();
        trie.put( "abc", 1 );
        Assert.assertTrue( 1 == trie.get( "abc" ) );
        trie.put( "ab", 2 );
        Assert.assertTrue(1 == trie.get( "abc" ));
        Assert.assertTrue(2 == trie.get( "ab" ));
        trie.put("abcd", 3);
        Assert.assertTrue(1 == trie.get( "abc" ));
        Assert.assertTrue(2 == trie.get( "ab" ));
        Assert.assertTrue(3 == trie.get( "abcd" ));
        trie.remove( "ab" );
        Assert.assertTrue(1 == trie.get( "abc" ));
        Assert.assertTrue(null == trie.get( "ab" ));
        Assert.assertTrue(3 == trie.get( "abcd" ));
        trie.remove("abc");
        Assert.assertTrue(null == trie.get( "abc" ));
        Assert.assertTrue(null == trie.get( "ab" ));
        Assert.assertTrue(3 == trie.get( "abcd" ));
        trie.remove("abcd");
        Assert.assertTrue(null == trie.get( "abc" ));
        Assert.assertTrue(null == trie.get( "ab" ));
        Assert.assertTrue(null == trie.get( "abcd" ));
    }

    @Test
    public void test2() {
        RadixTrie<Integer> trie = new RadixTrie<Integer>();
        trie.put( "ab", 1 );
        trie.put( "abcd", 2 );
        trie.put( "abce", 3 );
        trie.put( "abc", 4 );
        Assert.assertTrue(1 == trie.get( "ab" ));
        Assert.assertTrue(2 == trie.get( "abcd" ));
        Assert.assertTrue(3 == trie.get( "abce" ));
        Assert.assertTrue(4 == trie.get( "abc" ));
        Assert.assertTrue( 4 == trie.getDepth() );
    }

    @Test
    public void test3() {
        RadixTrie<Integer> trie = new RadixTrie<Integer>();
        trie.put( "abcde", 1 );
        trie.put( "abcd", 2 );
        trie.put( "abcdf", 3 );
        trie.put( "a", 4 );
        Assert.assertTrue(1 == trie.get( "abcde" ));
        Assert.assertTrue(2 == trie.get( "abcd" ));
        Assert.assertTrue(3 == trie.get( "abcdf" ));
        Assert.assertTrue(4 == trie.get( "a" ));
    }

    @Test
    public void testBestMatch() {
        RadixTrie<Integer> trie = new RadixTrie<Integer>();
        trie.put( "a", 1 );
        trie.put( "ab", 2 );
        trie.put( "abc", 3 );
        trie.put( "abcdef", 4 );
        RadixTrie.MatchResult<Integer> match = trie.getBestMatch( "abcde" );
        Assert.assertTrue( match.matchedKey.equals( "abc" ) );
        Assert.assertTrue( match.value == 3 );
    }
}
