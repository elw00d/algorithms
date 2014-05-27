import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * @author igor.kostromin
 *         22.05.2014 16:49
 */
public class RangeCoder {
    private final int alphabetSize;
    private final int PRECISION = 32;
    private final int BITS_IN_BYTE = 8;
    private final int MIN_RANGE = 1 << ( PRECISION - 1 - BITS_IN_BYTE);
    private final int[] probs;

    // размер алфавита <= 2^(PRECISION-1-BITS_IN_BYTE) (минимум по точке на символ в интервале MIN_RANGE)
    public RangeCoder(int alphabetSize){
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

        final int TOP = 1 << (PRECISION - 1);
        final int test = TOP - MIN_RANGE - 1;

        int low = 0;
        int range = 1 << (PRECISION - 1);
        int carry = 0;
        boolean nextByteInited = false;
        byte nextByte = 0;

        // Маска для сброса 32-ого бита с low (это необходимо после обработки переноса)
        int lowMask = TOP - 1;

        for (int i = 0; i < message.length; i++){
            int c = message[i];

            low = (int) (low + sumProbs[c] * (range & 0xffffffffL) / totalCount);
            range = (int) (probs[c] * (range & 0xffffffffL) / totalCount);

            if ( i < 10 || i + 10 > message.length) {
                System.out.println(String.format( "Low: %s Range: %s", Integer.toHexString( low ), Integer.toHexString( range ) ));
            }

            while (compareUnsigned(range, MIN_RANGE) <= 0){
                // todo : verify
                if (compareUnsigned( low , TOP - MIN_RANGE) < 0) {
                    // Сейчас мы видим, что переноса нет, т.к. весь интервал находится слева от TOP
                    // Поэтому если у нас до этого был перенос, мы сбрасываем carry байт 0xff в файл
                    if (nextByteInited)
                        stream.write( nextByte & 0xff );

                    for (int j = 0; j < carry; j++)
                        stream.write( 0xFF );
                    carry = 0;

                    // Но он может возникнуть в будущем, когда мы расширим интервал в 2^8 раз
                    // И этот потенциальный перенос может изменить текущий байт, поэтому сохраняем его в nextByte
                    nextByte = ( byte ) (0xFF & (low >> PRECISION - 1 - BITS_IN_BYTE));
                    nextByteInited = true;
                } else if ( compareUnsigned( low, TOP ) >= 0 ) {
                    // Рабочий интервал справа от TOP - значит, мы дошли до переноса, и нам нужно
                    // прибавить 1 к nextByte и сбросить carry нулевых байт в файл
                    stream.write( (nextByte + 1) & 0xff );

                    for (int j = 0; j < carry; j++)
                        stream.write( 0x00 );
                    carry = 0;

                    nextByte = ( byte ) (0xFF & (low >> PRECISION - 1 - BITS_IN_BYTE));
                } else {
                    // Старший байт low = 0xff, и мы должны увеличить счётчик carry
                    carry++;
                }

                low <<= 8;
                low &= lowMask;

                range <<= 8;
            }
        }

        // Завершаем кодирование
        if (message.length != 0) {
            if ( compareUnsigned( low , TOP) < 0 ) {
                stream.write( nextByte );
                for (; carry > 0; carry--)
                    stream.write( 0xff );
            } else{
                stream.write( nextByte + 1 );
                for (; carry > 0; carry--)
                    stream.write( 0x00 );
            }
            stream.write( (low >>> 23) & 0xff );
            stream.write( (low >>> (23 - 8)) & 0xff );
            // Так как нам нужны только старшие 23 бита, то
            // 24-ый бит несущественен, и маска = 0xfe
            stream.write( (low >>> (23 - 16)) & 0xfe );
            //stream.write( (low & 0x7f) << 1 );
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
        //byte b4 = readNextByte(inputStream);
        //int v = ((((((b1 & 0xff) << 8) | b2 & 0xff) << 8) | b3 & 0xff) << 8) | b4 & 0xff;
        int v = ((((((b1 & 0xff) << 8) | b2 & 0xff) << 8) | b3 & 0xff) << 8) >>> 1;
        return v;
    }

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

        final int TOP = 1 << (PRECISION - 1);
        // Маска для сброса 32-ого бита с low (это необходимо после обработки переноса)
        final int lowMask = TOP - 1;

        int low = 0;
        int range = 1 << (PRECISION - 1);
        for ( int i = 0; i < len; i++ ) {
            if ( i + 2 == len ) {
                System.out.println();
            }

            int threshold;
            if (compareUnsigned(value, low) >= 0)
                threshold = (int) ((((value - low + 1) & 0xffffffffL) * totalCount - 1) / (range & 0xffffffffL));
            else
                threshold = (int) ((((0x80000000L + value - low + 1) & 0xffffffffL) * totalCount - 1) / (range & 0xffffffffL));

            int c;
            for(c = 0; c < alphabetSize; c++){
                if (compareUnsigned( sumProbs[c] + probs[c], threshold) > 0) break;
            }

            message[i] = c;

            low = (int) (low + sumProbs[c] * (range & 0xffffffffL) / totalCount);
            range = (int) (probs[c] * (range & 0xffffffffL) / totalCount);

            if ( i < 10 || i + 10 > len) {
                System.out.println(String.format( "Low: %s Range: %s", Integer.toHexString( low ), Integer.toHexString( range ) ));
            }

            while (compareUnsigned(range , MIN_RANGE) <= 0){
                low <<= 8;
                low &= lowMask;
                value <<= 8;
                value &= lowMask;
                value |= (readNextByte( inputStream ) & 0xff) << 7;
                range <<= 8;
            }
        }

        return message;
    }
}
