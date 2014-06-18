import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * @author igor.kostromin
 *         19.05.2014 11:21
 */
public class ArithmeticCoder64 {
    private final int alphabetSize;
    private final int precision;
    private final long[] probs;

    private final long half;
    private final long qtr;

    // precision - степень двойки, от 2 до 64
    // размер алфавита <= 2^(precision-2) (минимум по точке на символ в 1/4 интервала)
    public ArithmeticCoder64( int alphabetSize, int precision ){
        assert !(precision < 2 || precision > 64);
        assert alphabetSize <= 1 << (precision - 2);
        this.alphabetSize = alphabetSize;
        this.probs = new long[alphabetSize];
        this.precision = precision;
        this.half = 1L << (precision - 1);
        this.qtr = 1L << (precision - 2);
    }

    public void initTest() {
        probs[0] = qtr - 3;
        probs[1] = 1;
        probs[2] = 1;
        probs[3] = 1;
    }

    // считает rawProbs и преобразует в probs, пригодные для кодирования
    // на выходе должны быть probs, в котором нет ни одного нулевого элемента,
    // а сумма всех значений не превышает qtr
    public void count(int[] message){
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
                if(maxCount == qtr){
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
        while (compareUnsigned( totalCount + alphabetSize, qtr) > 0) {
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
        assert compareUnsigned( calculatedTotalCount, qtr) <= 0;
    }

    private static int compareUnsigned(long a, long b){
        return Long.compare( a ^ 0x8000000000000000L, b ^ 0x8000000000000000L );
    }

    private static int compareUnsigned(int a, int b){
        return Integer.compare( a ^ 0x80000000, b ^ 0x80000000 );
    }

    private void outBit(int bit){
        if (bit == 1){
            currentByte |= bit << bitsUsed;
        }
        if (++bitsUsed == 8){
            stream.write( currentByte );
            currentByte = 0;
            bitsUsed = 0;
        }
    }

    private void outBits(int bit, long carry){
        outBit( bit );
        for(int i=0;i<carry;i++)
            outBit( bit==1?0:1 );
    }

    private void flushStream(){
        if(bitsUsed!=0){
            stream.write( currentByte );
        }
    }

    private ByteArrayOutputStream stream;
    private byte currentByte;
    private int bitsUsed;

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

    public ByteArrayOutputStream encode(int[] message) {
        stream = new ByteArrayOutputStream(  );

        // Накапливающаяся сумма встречаемости символов
        // Первый элемент - 0, второй - 0 + встречаемость первого, итд
        long[] sumProbs = new long[alphabetSize];
        for(int i = 0; i < alphabetSize; i++){
            sumProbs[i] = i > 0 ? sumProbs[i - 1] + probs[i - 1] : 0;
        }
        long totalCount = sumProbs[alphabetSize - 1] + probs[alphabetSize - 1];

        long firstQtr = 1L << (precision - 2);
        long thirdQtr = half + firstQtr;

        long left = 0;
        // Потому что в java 1L << 64 = 1L (эквивалентно 1L << 0)
        long right = precision == 64 ? -2 : ((1L << precision) - 1); // todo : при 64 будет неверный результат
        long carry = 0; // Сколько бит участвует в переносе
        for (int i = 0; i < message.length; i++){
            int c = message[i];
            long range = right - left + 1;
            assert compareUnsigned( range , qtr) >= 0;

            long oldLeft = left;
            assert unsignedDiv( range , totalCount) >= 1;
            left = oldLeft + sumProbs[c] * unsignedDiv(range , totalCount);
            right = oldLeft + (sumProbs[c] + probs[c]) * unsignedDiv(range , totalCount) - 1;

            // Normalize if need
            while (true){
                if(compareUnsigned( right, half - 1) <= 0){
                    // out 0 and drop carry
                    outBits( 0, carry );
                    carry=0;
                } else if (compareUnsigned( left , half) >= 0) {
                    // out 1 and drop carry
                    outBits( 1, carry );
                    carry=0;
                    left -= half;
                    right -= half;
                } else if (compareUnsigned( left , firstQtr) >= 0 && compareUnsigned( right , thirdQtr - 1) <= 0) {
                    carry++;
                    left -= qtr;
                    right -= qtr;
                } else break;
                left += left;
                right += right + 1;
                if (right == -1L)
                    right = right - 1;
            }
        }

        // Последние 2 бита, определяющие четверть интервала, в которой лежит счс искомое число
        // Это либо вторая четверть, либо третья (либо любая из них, если интервал включает их обе), поэтому вывод будет либо 01 либо 10
        // (если бы число лежало в 1ой или 4ой четверти, то предварительно была бы проведена нормализация)
        // На самом деле, мы можем допустить, что число может лежать и в 1ой или 4ой четверти (если текущий рабочий
        // интервал захватывает одну из них), но тогда нам потребуется больше бит для уточнения этих координат.
        // А 2ой или 3ий четверть-интервал захвачен текущим рабочим интервалом _целиком_, поэтому нам достаточно
        // взять первое число (самое левое) из этого четвертьинтервала.
        // Эти 2 бита необходимы, т.к. по текущему состоянию интервала может быть непонятно, где находится число.
        // У нас есть интервал, например, [0.1; 0.7) и если мы не записываем последние биты в файл, то это
        // равносильно тому, что мы выбрали из последнего интервала число 0, которое не входит в [0.1; 0.7), что неверно.
        // В данной ситуации мы можем должны вывести последние 2 бита 01 (т.к. из средних четверть-интервалов [0.25; 0.5) и [0.5; 0.75)
        // только первый покрывается полностью). Если бы наш рабочий интервал был [0.1; 0.75+), то мы бы могли вывести как 01, так и 10.
        // Все последующие биты уже не являются необходимыми.
        carry++;
        if ( compareUnsigned( left, firstQtr - 1 ) <= 0 ) {
            outBits( 0, carry );
        } else {
            outBits( 1, carry );
        }

        flushStream();
        return stream;
    }

    private byte readingByte;
    private int readedBits=8;

    private int readBit(ByteArrayInputStream inputStream){
        if(readedBits==8){
            int readed = inputStream.read();
            if(-1 == readed) //throw new IllegalStateException( "Unexpected end of stream" );
                return 0;
            readingByte= ( byte ) readed;
            readedBits=0;
        }
        if ((readingByte & (1 << readedBits++)) != 0)
            return 1;
        return 0;
    }

    private long readFirstNumber(ByteArrayInputStream inputStream){
        long n = 0L;
        for (int i = 0; i < precision; i++){
            int bit = readBit( inputStream );
            if (bit == 1){
                n |= 1L << (precision - i - 1);
            }
        }
        return n;
    }

    public int[] decode(ByteArrayInputStream inputStream, int len) {
        int[] message = new int[len];
        long value = readFirstNumber( inputStream );

        long left = 0;
        long right = precision == 64 ? -2 : (1L << precision) - 1;

        long valueMask = precision == 64 ? -1 : ((1L << precision) - 1);

        // Накапливающаяся сумма встречаемости символов
        // Первый элемент - 0, второй - 0 + встречаемость первого, итд
        long[] sumProbs = new long[alphabetSize];
        for(int i = 0; i < alphabetSize; i++){
            sumProbs[i] = i > 0 ? sumProbs[i - 1] + probs[i - 1] : 0;
        }
        long totalCount = sumProbs[alphabetSize - 1] + probs[alphabetSize - 1];

        long firstQtr = 1L << (precision - 2);
        long thirdQtr = half + firstQtr;

        for(int i = 0; i < len; i++){
            long range = right - left + 1;
            assert compareUnsigned( range , qtr) >= 0;

            // Найти такой элемент, left которого бы при кодировании был бы самым ближайшим слева
            int c;
//            int threshold = ( int ) ((((value & 0xFFFFFFFFL) - left + 1) * totalCount - 1) / range);
            long threshold = unsignedDiv (value - left, unsignedDiv(range , totalCount));
            for(c = 0; c < alphabetSize; c++){
                if (compareUnsigned( sumProbs[c] + probs[c], threshold) > 0) break;
            }
//            int c;
//            for(c = 0; c < alphabetSize; c++){
//                int jLeft = ( int ) (left + sumProbs[c] * (range / totalCount) );
//                int jRight = ( int ) (left + (sumProbs[c] + probs[c]) * (range / totalCount) - 1);
//                if(compareUnsigned(value, jLeft) >= 0 && compareUnsigned(value, jRight) <= 0){
//                    break;
//                }
//            }

            message[i] = c;

            long oldLeft = left;
            assert unsignedDiv( range , totalCount) >= 1;
            left = oldLeft + sumProbs[c] * unsignedDiv(range , totalCount);
            right = oldLeft + (sumProbs[c] + probs[c]) * unsignedDiv(range , totalCount) - 1;

            // Normalize if need
            while (true){
                if(compareUnsigned( right, half - 1) <= 0){
                } else if (compareUnsigned( left , half) >= 0) {
                    value -= half;
                    left -= half;
                    right -= half;
                } else if (compareUnsigned( left , firstQtr) >= 0 && compareUnsigned( right , thirdQtr - 1) <= 0) {
                    value -= qtr;
                    left -= qtr;
                    right -= qtr;
                } else break;
                left += left;
                right += right + 1;
                value <<= 1;
                value += readBit( inputStream );
                value &= valueMask;
                if (right == -1L)
                    right = right - 1;
            }
        }

        return message;
    }
}
