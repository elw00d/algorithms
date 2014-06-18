import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * @author igor.kostromin
 *         18.06.2014 10:20
 */
public class CarrylessRangeCoder64 {
    private final int alphabetSize;
    private final int PRECISION = 64;
    private final int BITS_IN_BYTE = 8;
    // На сколько бит уменьшается MIN_RANGE по сравнению с начальным 2^56
    private final int MIN_RANGE_LOWERIZE_BITS = 24;
    private final long MIN_RANGE = 1L << ( PRECISION - BITS_IN_BYTE - MIN_RANGE_LOWERIZE_BITS);
    private final int[] probs;

    // размер алфавита <= 2^(PRECISION-1-BITS_IN_BYTE) (минимум по точке на символ в интервале MIN_RANGE)
    public CarrylessRangeCoder64(int alphabetSize){
        assert alphabetSize <= MIN_RANGE;
        this.alphabetSize = alphabetSize;
        this.probs = new int[alphabetSize];
    }

    // считает rawProbs и преобразует в probs, пригодные для кодирования
    // на выходе должны быть probs, в котором нет ни одного нулевого элемента,
    // а сумма всех значений не превышает 2^8
    public void count(int[] message){
        final long totalCountTreshold = MIN_RANGE;
        int[] rawProbs = new int[alphabetSize];

        // Сначала просто считаем количество каждого элемента, нормализуя их если
        // чьё-то кол-во превосходит qtr (для избежания переполнения)
        int maxCount = 0;
        for(int i = 0; i < message.length; i++){
            int prob = ++rawProbs[message[i]];
            if(prob > maxCount){
                maxCount = prob;

                // Если для одного из символов кол-во достигло qtr, нужно нормализовать
                // все вероятности, поделив кол-во каждого на 2
                if(maxCount == totalCountTreshold){
                    for(int j = 0; j < alphabetSize; j++){
                        rawProbs[j] <<= 1;
                    }
                    maxCount <<= 1;
                }
            }
        }

        // Теперь считаем общую сумму накопленных значений для того, чтобы окончательно
        // нормализовать массив probs.
        int totalCount = 0;
        for (int i = 0; i < rawProbs.length; i++)
            totalCount += rawProbs[i];

        // Если totalCount + alphabetSize > qtr, нужно выполнить нормализацию массива
        // до тех пор, пока не будет выполнено равенство totalCount + alphabetSize <= qtr
        // Здесь alphabetSize необходим для того, чтобы учесть возможные случаи того, что после
        // нормализации часть алфавита получит нулевые значения (а мы должны установить им хотя бы по 1).
        int shiftBits = 0;
        while (compareUnsigned( totalCount + alphabetSize, totalCountTreshold) > 0) {
            totalCount >>>= 1;
            shiftBits++;
        }

        // Тот totalCount, на который мы ориентируемся, на самом деле может не совпадать с тем, который
        // действительно будет получен после нормализации массива на shiftBits вправо (из-за того, что
        // элементы будут нормализованы отдельно), но оцененный нами totalCount будет всегда больше
        // действительно полученного, следовательно, наша оценка в любом случае будет верной.
        for (int i = 0; i < alphabetSize; i++){
            int v = rawProbs[i] >>> shiftBits;
            probs[i] = v == 0 ? 1 : v;
        }

        // Заключительная проверка
        int calculatedTotalCount = 0;
        for (int i = 0; i < alphabetSize; i++)
            calculatedTotalCount += probs[i];
        assert compareUnsigned( calculatedTotalCount, totalCountTreshold) <= 0;
    }

    private static int compareUnsigned(long a, long b){
        return Long.compare( a ^ 0x8000000000000000L, b ^ 0x8000000000000000L );
    }

    private static int compareUnsigned(int a, int b){
        return Integer.compare( a ^ 0x80000000, b ^ 0x80000000 );
    }

    public static int unsignedDiv( int dividend, int divisor ) {
        return ( int ) ((dividend & 0xffffffffL ) / (divisor & 0xffffffffL ));
    }

    /**
     * Compares the two specified {@code long} values, treating them as unsigned values between
     * {@code 0} and {@code 2^64 - 1} inclusive.
     *
     * @param a the first unsigned {@code long} to compare
     * @param b the second unsigned {@code long} to compare
     * @return a negative value if {@code a} is less than {@code b}; a positive value if {@code a} is
     * greater than {@code b}; or zero if they are equal
     */
    public static int compare( long a, long b ) {
        long a1 = a ^ Long.MIN_VALUE;
        long b1 = b ^ Long.MIN_VALUE;
        return (a1 < b1) ? -1 : ((a1 > b1) ? 1 : 0);
    }

    /**
     * from Guava source code:
     * <p/>
     * Returns dividend / divisor, where the dividend and divisor are treated as unsigned 64-bit
     * quantities.
     *
     * @param dividend the dividend (numerator)
     * @param divisor  the divisor (denominator)
     * @throws ArithmeticException if divisor is 0
     */
    public static long unsignedDiv( long dividend, long divisor ) {
        if ( divisor < 0 ) { // i.e., divisor >= 2^63:
            if ( compare( dividend, divisor ) < 0 ) {
                return 0; // dividend < divisor
            } else {
                return 1; // dividend >= divisor
            }
        }

        // Optimization - use signed division if dividend < 2^63
        if ( dividend >= 0 ) {
            return dividend / divisor;
        }

        /*
         * Otherwise, approximate the quotient, check, and correct if necessary. Our approximation is
         * guaranteed to be either exact or one less than the correct value. This follows from fact
         * that floor(floor(x)/i) == floor(x/i) for any real x and integer i != 0. The proof is not
         * quite trivial.
         */
        long quotient = ((dividend >>> 1) / divisor) << 1;
        long rem = dividend - quotient * divisor;
        return quotient + (compare( rem, divisor ) >= 0 ? 1 : 0);
    }

    private ByteArrayOutputStream stream;

    public ByteArrayOutputStream encode(int[] message) {
        stream = new ByteArrayOutputStream(  );

        // Накапливающаяся сумма встречаемости символов
        // Первый элемент - 0, второй - 0 + встречаемость первого, итд
        int[] sumProbs = new int[alphabetSize];
        for(int i = 0; i < alphabetSize; i++){
            sumProbs[i] = i > 0 ? sumProbs[i - 1] + probs[i - 1] : 0;
        }
        int totalCount = sumProbs[alphabetSize - 1] + probs[alphabetSize - 1];

        long low = 0;
        long range = -1;//(1L << PRECISION) - 1;

        for (int i = 0; i < message.length; i++){
            int c = message[i];

            low = low + sumProbs[c] * unsignedDiv(range , totalCount);
            range = probs[c] * unsignedDiv(range , totalCount);

            // True if top 8 bits are equal
            boolean highBitsEq;
            while ((highBitsEq = compareUnsigned((low ^ (low+range)), 0x100000000000000L) < 0)
                    || (compareUnsigned( range , MIN_RANGE) < 0)){
                if (!highBitsEq) range= -low & (MIN_RANGE-1);
                stream.write(( byte ) (0xff & (low >> (PRECISION - BITS_IN_BYTE))) );
                low <<= 8;
                range <<= 8;
            }
        }

        // Завершаем кодирование
        // todo : добавить формулу определения кол-ва бит, необходимых для вывода в файл при завершении
        // в зависимости от выбранного размера MIN_RANGE
        if (message.length != 0) {
            stream.write( ( int ) ((low >>> 56) & 0xff) );
            stream.write( ( int ) ((low >>> (56 - 8)) & 0xff) );
            stream.write( ( int ) ((low >>> (56 - 16)) & 0xff) );
            stream.write( ( int ) ((low >>> (56 - 24)) & 0xff) );
            stream.write( ( int ) ((low >>> (56 - 32)) & 0xff) );
            stream.write( ( int ) ((low >>> (56 - 40)) & 0xff) );
            stream.write( ( int ) ((low >>> (56 - 48)) & 0xff) );
            stream.write( ( int ) (low & 0xff) );
        }

        return stream;
    }

    private byte readNextByte(ByteArrayInputStream inputStream) {
        int readed = inputStream.read();
        if (-1 == readed) return 0;
        return ( byte ) readed;
    }

    private long readFirstNumber(ByteArrayInputStream inputStream){
        byte b1 = readNextByte(inputStream);
        byte b2 = readNextByte(inputStream);
        byte b3 = readNextByte(inputStream);
        byte b4 = readNextByte(inputStream);
        int v1 = ((((((b1 & 0xff) << 8) | b2 & 0xff) << 8) | b3 & 0xff) << 8) | b4 & 0xff;

        b1 = readNextByte(inputStream);
        b2 = readNextByte(inputStream);
        b3 = readNextByte(inputStream);
        b4 = readNextByte(inputStream);
        int v2 = ((((((b1 & 0xff) << 8) | b2 & 0xff) << 8) | b3 & 0xff) << 8) | b4 & 0xff;

        return ((v1 & 0xffffffffL) << 32) | (v2 & 0xffffffffL);
    }

    public int[] decodeOptimized(ByteArrayInputStream inputStream, int len) {
        int[] message = new int[len];
        long value = readFirstNumber( inputStream );

        // Накапливающаяся сумма встречаемости символов
        // Первый элемент - 0, второй - 0 + встречаемость первого, итд
        int[] sumProbs = new int[alphabetSize];
        for ( int i = 0; i < alphabetSize; i++ ) {
            sumProbs[i] = i > 0 ? sumProbs[i - 1] + probs[i - 1] : 0;
        }
        int totalCount = sumProbs[alphabetSize - 1] + probs[alphabetSize - 1];

        long low = 0;
        long range = -1;//(1L << PRECISION) - 1;

        for ( int i = 0; i < len; i++ ) {
            long threshold = unsignedDiv( (value - low), unsignedDiv( range, totalCount ) );

            int c;
            for(c = 0; c < alphabetSize; c++){
                if (compareUnsigned( sumProbs[c] + probs[c], threshold) > 0) break;
            }

            message[i] = c;

            low = low + sumProbs[c] * unsignedDiv (range , totalCount);
            range = probs[c] * unsignedDiv (range , totalCount);

            // True if top 8 bits are equal
            boolean highBitsEq;
            while ((highBitsEq = compareUnsigned((low ^ (low+range)), 0x100000000000000L) < 0)
                    || (compareUnsigned( range , MIN_RANGE) < 0)){
                if (!highBitsEq) range= -low & (MIN_RANGE-1);
                low <<= 8;
                value = (value << 8) | (readNextByte( inputStream ) & 0xff);
                range <<= 8;
            }
        }

        return message;
    }
}
