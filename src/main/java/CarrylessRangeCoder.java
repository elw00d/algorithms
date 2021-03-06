import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * 32-битная реализация субботинского интервального кодера.
 *
 * @author igor.kostromin
 *         02.06.2014 11:17
 */
public class CarrylessRangeCoder {
    private final int alphabetSize;
    private final int PRECISION = 32;
    private final int BITS_IN_BYTE = 8;
    // На сколько бит уменьшается MIN_RANGE по сравнению с начальным 2^24
    private final int MIN_RANGE_LOWERIZE_BITS = 7;
    private final int MIN_RANGE = 1 << ( PRECISION - BITS_IN_BYTE - MIN_RANGE_LOWERIZE_BITS);
    private final int[] probs;

    // размер алфавита <= 2^(PRECISION-1-BITS_IN_BYTE) (минимум по точке на символ в интервале MIN_RANGE)
    public CarrylessRangeCoder(int alphabetSize){
        assert alphabetSize <= MIN_RANGE;
        this.alphabetSize = alphabetSize;
        this.probs = new int[alphabetSize];
    }

    // считает rawProbs и преобразует в probs, пригодные для кодирования
    // на выходе должны быть probs, в котором нет ни одного нулевого элемента,
    // а сумма всех значений не превышает 2^8
    public void count(int[] message){
        final int totalCountTreshold = MIN_RANGE;
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

    private ByteArrayOutputStream stream;

    /**
     * Алгоритм кодирования, соответствующий первой версии carryless range coder'а
     * Дмитрия Субботина. Здесь выполняется принудительное уменьшение интервала, но нормализация
     * выполняется только при достижении MIN_RANGE. Этот код оставлен только для демонстрации.
     *
     * @param message
     * @return
     */
    public ByteArrayOutputStream encodeUnoptimized(int[] message) {
        stream = new ByteArrayOutputStream(  );

        // Накапливающаяся сумма встречаемости символов
        // Первый элемент - 0, второй - 0 + встречаемость первого, итд
        int[] sumProbs = new int[alphabetSize];
        for(int i = 0; i < alphabetSize; i++){
            sumProbs[i] = i > 0 ? sumProbs[i - 1] + probs[i - 1] : 0;
        }
        int totalCount = sumProbs[alphabetSize - 1] + probs[alphabetSize - 1];

        int low = 0;
        int range = (int) ((1L << PRECISION) - 1);

        // 24-битное число, в котором MIN_RANGE_LOWERIZE_BITS старших бит установлено в 1
        // для MIN_RANGE = 2^16 - 0xff0000 (MIN_RANGE_LOWERIZE_BITS = 8), для MIN_RANGE = 2^24 - 0x000000 (MIN_RANGE_LOWERIZE_BITS = 0)
        int mask = (~((1 << (24-MIN_RANGE_LOWERIZE_BITS)) - 1)) & 0xffffff;

        for (int i = 0; i < message.length; i++){
            int c = message[i];

            low = low + sumProbs[c] * unsignedDiv(range , totalCount);
            range = probs[c] * unsignedDiv(range , totalCount);

            assert (low & 0xffffffffL) + range - 1 < (1L << PRECISION) - 1;
            while (compareUnsigned(range, MIN_RANGE) <= 0){
                if ( (low & mask) == mask &&
                        compareUnsigned(range + (low & (MIN_RANGE - 1) - 1), MIN_RANGE) >= 0 ){
                    range = MIN_RANGE - (low & (MIN_RANGE - 1));
                }
                stream.write(( byte ) (0xff & (low >> (PRECISION - BITS_IN_BYTE))) );
                low <<= 8;
                range <<= 8;
            }
        }

        // Завершаем кодирование
        if (message.length != 0) {
            stream.write( (low >>> 24) & 0xff );
            stream.write( (low >>> (24 - 8)) & 0xff );
            stream.write( (low >>> (24 - 16)) & 0xff );
            stream.write( low & 0xff );
        }

        return stream;
    }

    /**
     * Алгоритм кодирования, соответствующий второй версии carryless range coder'а
     * Дмитрия Субботина. Здесь присутствует дополнительная оптимизация, при которой нормализация
     * производится сразу же, как только старшие байты в low и low+range перестают меняться -
     * это увеличивает точность кодирования. Это и есть основной вариант субботинского кодера.
     *
     * @param message
     * @return
     */
    public ByteArrayOutputStream encode(int[] message) {
        stream = new ByteArrayOutputStream(  );

        // Накапливающаяся сумма встречаемости символов
        // Первый элемент - 0, второй - 0 + встречаемость первого, итд
        int[] sumProbs = new int[alphabetSize];
        for(int i = 0; i < alphabetSize; i++){
            sumProbs[i] = i > 0 ? sumProbs[i - 1] + probs[i - 1] : 0;
        }
        int totalCount = sumProbs[alphabetSize - 1] + probs[alphabetSize - 1];

        int low = 0;
        int range = (int) ((1L << PRECISION) - 1);

        for (int i = 0; i < message.length; i++){
            int c = message[i];

            low = low + sumProbs[c] * unsignedDiv(range , totalCount);
            range = probs[c] * unsignedDiv(range , totalCount);

            assert (low & 0xffffffffL) + range - 1 < (1L << PRECISION) - 1;
            while (compareUnsigned((low ^ (low+range)), 0x1000000) < 0
                    || (compareUnsigned( range , MIN_RANGE) < 0)){ // if top 8 bits are equal
                if (compareUnsigned((low ^ low+range), 0x1000000) < 0) {
                } else if (compareUnsigned( range , MIN_RANGE) < 0){
                    assert MIN_RANGE - (low & (MIN_RANGE - 1)) == (-low & (MIN_RANGE-1));
//                    range = MIN_RANGE - (low & (MIN_RANGE - 1));
                    range= -low & (MIN_RANGE-1);
                }
                stream.write(( byte ) (0xff & (low >> (PRECISION - BITS_IN_BYTE))) );
                low <<= 8;
                range <<= 8;
            }
        }

        // Завершаем кодирование
        if (message.length != 0) {
            stream.write( (low >>> 24) & 0xff );
            stream.write( (low >>> (24 - 8)) & 0xff );
            stream.write( (low >>> (24 - 16)) & 0xff );
            stream.write( low & 0xff );
        }

        return stream;
    }

    private byte readNextByte(ByteArrayInputStream inputStream) {
        int readed = inputStream.read();
        if (-1 == readed) return 0;
        return ( byte ) readed;
    }

    private int readFirstNumber(ByteArrayInputStream inputStream){
        byte b1 = readNextByte(inputStream);
        byte b2 = readNextByte(inputStream);
        byte b3 = readNextByte(inputStream);
        byte b4 = readNextByte(inputStream);
        return ((((((b1 & 0xff) << 8) | b2 & 0xff) << 8) | b3 & 0xff) << 8) | b4 & 0xff;
    }

    /**
     * Алгоритм декодирования, соответствующий методу {@link #encodeUnoptimized(int[])}.
     */
    public int[] decodeUnoptimized(ByteArrayInputStream inputStream, int len) {
        int[] message = new int[len];
        int value = readFirstNumber( inputStream );

        // Накапливающаяся сумма встречаемости символов
        // Первый элемент - 0, второй - 0 + встречаемость первого, итд
        int[] sumProbs = new int[alphabetSize];
        for ( int i = 0; i < alphabetSize; i++ ) {
            sumProbs[i] = i > 0 ? sumProbs[i - 1] + probs[i - 1] : 0;
        }
        int totalCount = sumProbs[alphabetSize - 1] + probs[alphabetSize - 1];

        int low = 0;
        int range = ( int ) ((1L << PRECISION) - 1);

        // 24-битное число, в котором MIN_RANGE_LOWERIZE_BITS старших бит установлено в 1
        // для MIN_RANGE = 2^16 - 0xff0000 (MIN_RANGE_LOWERIZE_BITS = 8), для MIN_RANGE = 2^24 - 0x000000 (MIN_RANGE_LOWERIZE_BITS = 0)
        int mask = (~((1 << (24-MIN_RANGE_LOWERIZE_BITS)) - 1)) & 0xffffff;

        for ( int i = 0; i < len; i++ ) {
            // Следующее необходимо для того, чтобы выполнить вычитание между двумя 31-битными числами: ((value - low) & 0x7fffffffL)
            // Оно работает, причём даже в случае если числа имеют установленные 32-ые биты, поэтому нам не надо принудительно
            // выполнять value &= lowMask и low &= lowMask ни при их изменении, ни перед вычитанием.
            int threshold = unsignedDiv ((value - low) , unsignedDiv( range , totalCount));

            int c;
            for(c = 0; c < alphabetSize; c++){
                if (compareUnsigned( sumProbs[c] + probs[c], threshold) > 0) break;
            }

            message[i] = c;

            low = low + sumProbs[c] * unsignedDiv (range , totalCount);
            range = probs[c] * unsignedDiv (range , totalCount);

            assert (low & 0xffffffffL) + range - 1 < (1L << PRECISION) - 1;
            while (compareUnsigned(range , MIN_RANGE) <= 0){
                if ( (low & mask) == mask &&
                        compareUnsigned(range + (low & (MIN_RANGE - 1)) - 1, MIN_RANGE) >= 0 ){
                    range = MIN_RANGE - (low & (MIN_RANGE - 1));
                }

                low <<= 8;
                value = (value << 8) | (readNextByte( inputStream ) & 0xff);
                range <<= 8;
            }

            // Убеждаемся, что мы никогда не выходим за рамки 32-битового числа
            // Low может выходить за пределы 31-битового числа, но Low+Range - всегда должны помещаться в 32 бита
            assert compareUnsigned( (low & 0xffffffffL) + (range & 0xffffffffL), 0x100000000L ) <= 0;
        }

        return message;
    }

    /**
     * Алгоритм декодирования, соответствующий методу {@link #encode(int[])}.
     */
    public int[] decode(ByteArrayInputStream inputStream, int len) {
        int[] message = new int[len];
        int value = readFirstNumber( inputStream );

        // Накапливающаяся сумма встречаемости символов
        // Первый элемент - 0, второй - 0 + встречаемость первого, итд
        int[] sumProbs = new int[alphabetSize];
        for ( int i = 0; i < alphabetSize; i++ ) {
            sumProbs[i] = i > 0 ? sumProbs[i - 1] + probs[i - 1] : 0;
        }
        int totalCount = sumProbs[alphabetSize - 1] + probs[alphabetSize - 1];

        int low = 0;
        int range = ( int ) ((1L << PRECISION) - 1);

        for ( int i = 0; i < len; i++ ) {
            // Следующее необходимо для того, чтобы выполнить вычитание между двумя 31-битными числами: ((value - low) & 0x7fffffffL)
            // Оно работает, причём даже в случае если числа имеют установленные 32-ые биты, поэтому нам не надо принудительно
            // выполнять value &= lowMask и low &= lowMask ни при их изменении, ни перед вычитанием.
            int threshold = unsignedDiv ((value - low) , unsignedDiv( range , totalCount));

            int c;
            for(c = 0; c < alphabetSize; c++){
                if (compareUnsigned( sumProbs[c] + probs[c], threshold) > 0) break;
            }

            message[i] = c;

            low = low + sumProbs[c] * unsignedDiv (range , totalCount);
            range = probs[c] * unsignedDiv (range , totalCount);

            assert (low & 0xffffffffL) + range - 1 < (1L << PRECISION) - 1;
            // todo : упростить как это сделано в 64-битной версии
            while (compareUnsigned((low ^ (low+range)), 0x1000000) < 0
                    || (compareUnsigned( range , MIN_RANGE) < 0)){ // if top 8 bits are equal
                if (compareUnsigned((low ^ low+range), 0x1000000) < 0) {
                } else if (compareUnsigned( range , MIN_RANGE) < 0){
                    assert MIN_RANGE - (low & (MIN_RANGE - 1)) == (-low & (MIN_RANGE-1));
                    // todo : доказать эквивалентность этих строк
//                    range = MIN_RANGE - (low & (MIN_RANGE - 1));
                    range= -low & (MIN_RANGE-1);
                }
                low <<= 8;
                value = (value << 8) | (readNextByte( inputStream ) & 0xff);
                range <<= 8;
            }

            // Убеждаемся, что мы никогда не выходим за рамки 32-битового числа
            // Low может выходить за пределы 31-битового числа, но Low+Range - всегда должны помещаться в 32 бита
            assert compareUnsigned( (low & 0xffffffffL) + (range & 0xffffffffL), 0x100000000L ) <= 0;
        }

        return message;
    }
}
