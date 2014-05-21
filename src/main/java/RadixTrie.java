import java.util.*;

/**
 * This map doesn't allow to store nulls.
 *
 * @author igor.kostromin
 *         13.05.2014 16:26
 */
public class RadixTrie<T> {
    private static class Node<T> implements Comparable<Node<T>> {
        String str;
        T o;
        List<Node<T>> children;

        private Node( String str ) {
            this.str = str;
        }

        @Override
        public String toString() {
            if(children != null&&!children.isEmpty()){
                StringBuilder sb = new StringBuilder(  );
                sb.append( "'" ).append( children.get( 0 ).str ).append( "'" );
                for ( int i = 1; i < children.size(); i++ ) {
                    Node child = children.get( i );
                    sb.append( ',' ).append( "'" ).append( child.str ).append( "'" );
                }
                return String.format( "Node{'%s',children=[%s]}", str, sb );
            }
            return String.format( "Node{'%s'}", str );
        }

        @Override
        public int compareTo( Node<T> o ) {
            return Character.compare( this.str.charAt( 0 ), o.str.charAt( 0 ) );
        }
    }

    private Node<T> root;

    public RadixTrie() {
        root = new Node<T>("");
    }

    public int size() {
        return getNodeSize( root );
    }

    /**
     * Recursive method, should be optimized to iterative if will be used in depth tries.
     */
    private int getNodeSize(Node<T> node) {
        int size = node.o != null ? 1 : 0;
        if (node.children != null){
            for ( Node<T> child : node.children ) {
                size += getNodeSize( child );
            }
        }
        return size;
    }

    public boolean isEmpty() {
        return root.children == null || root.children.isEmpty();
    }

    /**
     * In empty trie returns 1 (because root node always presents).
     */
    public int getDepth() {
        return getNodeDepth( root );
    }

    private int getNodeDepth(Node<T> node){
        int maxDepth = 0;
        if (node.children!=null){
            for ( Node<T> child : node.children ) {
                int childDepth = getNodeDepth( child );
                if ( childDepth > maxDepth ){
                    maxDepth = childDepth;
                }
            }
        }
        return maxDepth + 1;
    }

    public static class MatchResult<T> {
        public final T value;
        public final String matchedKey;

        public MatchResult( String matchedKey, T value ) {
            this.matchedKey = matchedKey;
            this.value = value;
        }
    }

    private MatchResult<T> getMatchResult(String key, Node<T> node, List<Node<T>> path, int i){
        if (node.o != null){
            return new MatchResult<T>( key.substring( 0, i ), node.o );
        }
        // Find first node in path with non-null value (looking back)
        int k = path.size() - 1;
        Node<T> n = path.get( k );
        while(n.o == null&&k >= 0){
            k--;
            n = path.get( k );
        }
        if (k < 0) return null;
        int off = 0;
        for (int j = 0; j < k; j++){
            off += path.get( j ).str.length();
        }
        return new MatchResult<T>( key.substring( 0, off ), node.o );
    }

    public MatchResult<T> getBestMatch( String key ) {
        Node<T> node = root;
        int i = 0;
        List<Node<T>> path = new ArrayList<Node<T>>(  );
        while (true) {
            if ( node.children == null ) return null;
            Node<T> candidate = null;
            char c = key.charAt( i );

            // Experimentally optimal value
            if (node.children.size() < 25) {
                for ( Node<T> child : node.children ) {
                    if ( child.str.charAt( 0 ) == c ) {
                        candidate = child;
                        break;
                    }
                }
            } else {
                int i1 = Collections.binarySearch( node.children, new Node<T>( key.substring( i, i + 1 ) ) );
                if ( i1 >= 0 )
                    candidate = node.children.get( i1 );
            }

            if ( null == candidate ) return getMatchResult(key, node, path, i );
            if ( key.length() - i < candidate.str.length() ) return getMatchResult(key, node, path, i );
            // If candidate matches substr in keyStr at i-th position
            boolean match = true;
            for ( int k = 0, len = candidate.str.length(); k < len; k++ ) {
                if ( key.charAt( i + k ) != candidate.str.charAt( k ) ) {
                    match = false;
                    break;
                }
            }
            if ( match ) {
                i += candidate.str.length();
                // If keyStr ends with candidate.str (after i-th char)
                if ( i == key.length() ) return new MatchResult<T>( key, candidate.o );
            } else return getMatchResult(key, node, path, i );
            path.add( node );
            node = candidate;
        }
    }

    public T get( String key ) {
        Node<T> node = root;
        int i = 0;
        while (true) {
            if ( node.children == null ) return null;
            Node<T> candidate = null;
            char c = key.charAt( i );

            // Experimentally optimal value
            if (node.children.size() < 25) {
                for ( Node<T> child : node.children ) {
                    if ( child.str.charAt( 0 ) == c ) {
                        candidate = child;
                        break;
                    }
                }
            } else {
                int i1 = Collections.binarySearch( node.children, new Node<T>( key.substring( i, i + 1 ) ) );
                if ( i1 >= 0 )
                    candidate = node.children.get( i1 );
            }

            if ( null == candidate ) return null;
            if ( key.length() - i < candidate.str.length() ) return null;
            // If candidate matches substr in keyStr at i-th position
            boolean match = true;
            for ( int k = 0, len = candidate.str.length(); k < len; k++ ) {
                if ( key.charAt( i + k ) != candidate.str.charAt( k ) ) {
                    match = false;
                    break;
                }
            }
            if ( match ) {
                i += candidate.str.length();
                // If keyStr ends with candidate.str (after i-th char)
                if ( i == key.length() ) return candidate.o;
            } else return null;
            node = candidate;
        }
    }

    public T put( String key, T value ) {
        if (null == value) throw new IllegalArgumentException( "value cannot be null" );

        Node<T> node = root;
        int i = 0;
        while (true) {
            if ( node.children == null ) {
                node.children = new ArrayList<Node<T>>( 1 );
                Node<T> newNode = new Node<T>( key.substring( i ) );
                newNode.o = value;
                node.children.add( newNode );
                Collections.sort( node.children );
                return null;
            }
            Node<T> candidate = null;
            char c = key.charAt( i );
            for ( Node<T> child : node.children ) {
                if ( child.str.charAt( 0 ) == c ) {
                    candidate = child;
                    break;
                }
            }
            if ( null == candidate ) {
                Node<T> newNode = new Node<T>( key.substring( i ) );
                newNode.o = value;
                node.children.add( newNode );
                Collections.sort(node.children);
                return null;
            }
            if ( key.length() - i < candidate.str.length() && candidate.str.startsWith( key.substring( i ) ) ) {
                // Split candidate to 2 parts
                // First will point to value, second - to original candidate value
                Node<T> newNode = new Node<T>( key.substring( i ) );
                newNode.o = value;
                newNode.children = new ArrayList<Node<T>>( 1 );
                newNode.children.add( candidate );

                candidate.str = candidate.str.substring( key.length() - i );

                node.children.remove( candidate );
                node.children.add( newNode );
                Collections.sort(node.children);

                return null;
            }
            // If candidate matches substr in keyStr at i-th position
            boolean match = true;
            int k = 0;
            for ( int len = candidate.str.length(); k < len; k++ ) {
                if ( key.charAt( i + k ) != candidate.str.charAt( k ) ) {
                    match = false;
                    break;
                }
            }
            if ( match ) {
                i += candidate.str.length();
                // If keyStr ends with candidate.str (after i-th char)
                if ( i == key.length() ) {
                    T oldValue = candidate.o;
                    candidate.o = value;
                    return oldValue;
                }
            } else {
                // First k chars of candidate matches keyStr after i-th pos
                // We need to split candidate to 3 nodes: common matching start, and 2 suffixes
                Node<T> commonParent = new Node<T>( candidate.str.substring( 0, k ) );
                commonParent.o = null;
                commonParent.children = new ArrayList<Node<T>>( 1 );
                commonParent.children.add( candidate );

                candidate.str = candidate.str.substring( k );

                Node<T> newChild = new Node<T>( key.substring( i + k ) );
                newChild.o = value;
                commonParent.children.add( newChild );

                node.children.remove( candidate );
                node.children.add( commonParent );
                Collections.sort(node.children);
                Collections.sort(commonParent.children);

                return null;
            }
            node = candidate;
        }
    }

    public T remove( String key ) {
        Node<T> node = root;
        int i = 0;
        while (true) {
            if ( node.children == null ) return null;
            Node<T> candidate = null;
            char c = key.charAt( i );
            for ( Node<T> child : node.children ) {
                if ( child.str.charAt( 0 ) == c ) {
                    candidate = child;
                    break;
                }
            }
            if ( null == candidate ) return null;
            if ( key.length() - i < candidate.str.length() ) return null;
            // If candidate matches substr in keyStr at i-th position
            boolean match = true;
            for ( int k = 0, len = candidate.str.length(); k < len; k++ ) {
                if ( key.charAt( i + k ) != candidate.str.charAt( k ) ) {
                    match = false;
                    break;
                }
            }
            if ( match ) {
                i += candidate.str.length();
                // If keyStr ends with candidate.str (after i-th char)
                if ( i == key.length() ) {
                    T result = candidate.o;

                    // Remove value for candidate node
                    candidate.o = null;

                    if (candidate.children == null || candidate.children.isEmpty()) {
                        node.children.remove( candidate );
                    } else if (candidate.children.size() == 1) {
                        // Union candidate with children
                        node.children.remove( candidate );
                        Node<T> child = candidate.children.get( 0 );
                        child.str = candidate.str + child.str;
                        node.children.add( child );
                        Collections.sort(node.children);
                    }

                    return result;
                }
            }
            node = candidate;
        }
    }

    public void clear() {
        root.children = null;
        root.o = null;
    }
}
