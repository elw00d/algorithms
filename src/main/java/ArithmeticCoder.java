import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * @author igor.kostromin
 *         19.05.2014 11:21
 */
public class ArithmeticCoder {
    private final int alphabetSize;
    private final int precision;
    private final int[] probs;

    private final int half;
    private final int qtr;

    // precision - степень двойки, от 2 до 32
    // размер алфавита <= 2^(precision-2) (минимум по точке на символ в 1/4 интервала)
    public ArithmeticCoder(int alphabetSize, int precision){
        assert !(precision < 2 || precision > 32);
        assert alphabetSize <= 1 << (precision - 2);
        this.alphabetSize = alphabetSize;
        this.probs = new int[alphabetSize];
        this.precision = precision;
        this.half = 1 << (precision - 1);
        this.qtr = 1 << (precision - 2);
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

    private void outBits(int bit, int carry){
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

    public ByteArrayOutputStream encode(int[] message) {
        stream = new ByteArrayOutputStream(  );

        // Накапливающаяся сумма встречаемости символов
        // Первый элемент - 0, второй - 0 + встречаемость первого, итд
        int[] sumProbs = new int[alphabetSize];
        for(int i = 0; i < alphabetSize; i++){
            sumProbs[i] = i > 0 ? sumProbs[i - 1] + probs[i - 1] : 0;
        }
        int totalCount = sumProbs[alphabetSize - 1] + probs[alphabetSize - 1];

        int firstQtr = 1 << (precision - 2);
        int thirdQtr = half + firstQtr;

        int left = 0;
        int right = ( int ) ((1L << precision) - 1);
        int carry = 0; // Сколько бит участвует в переносе
        for (int i = 0; i < message.length; i++){
            int c = message[i];
            long range = (right & 0xFFFFFFFFL) - (left & 0xFFFFFFFFL) + 1;
            assert compareUnsigned( range , qtr) >= 0;

            int oldLeft = left;
            left = ( int ) (oldLeft + sumProbs[c] * (range) / totalCount );
            right = ( int ) (oldLeft + (sumProbs[c] + probs[c]) * (range) / totalCount - 1);

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
                    //carry++;
                    //left -= qtr;
                    //right -= qtr;
                    right = half - 1;
                    continue;
                } else break;
                left += left;
                right += right + 1;
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

    private int readFirstNumber(ByteArrayInputStream inputStream){
        int n = 0;
        for (int i = 0; i < precision; i++){
            int bit = readBit( inputStream );
            if (bit == 1){
                n |= 1 << (precision - i - 1);
            }
        }
        return n;
    }

    public int[] decode(ByteArrayInputStream inputStream, int len) {
        int[] message = new int[len];
        int value = readFirstNumber( inputStream );

        int left = 0;
        int right = ( int ) ((1L << precision) - 1);

        int valueMask = ( int ) ((1L << precision) - 1);

        // Накапливающаяся сумма встречаемости символов
        // Первый элемент - 0, второй - 0 + встречаемость первого, итд
        int[] sumProbs = new int[alphabetSize];
        for(int i = 0; i < alphabetSize; i++){
            sumProbs[i] = i > 0 ? sumProbs[i - 1] + probs[i - 1] : 0;
        }
        int totalCount = sumProbs[alphabetSize - 1] + probs[alphabetSize - 1];

        int firstQtr = 1 << (precision - 2);
        int thirdQtr = half + firstQtr;

        for(int i = 0; i < len; i++){
            long range = (right & 0xFFFFFFFFL) - (left & 0xFFFFFFFFL) + 1;
            assert compareUnsigned( range , qtr) >= 0;

            // Найти такой элемент, left которого бы при кодировании был бы самым ближайшим слева
            int c;
            int threshold = ( int ) ((((value & 0xFFFFFFFFL) - left + 1) * totalCount - 1) / range);
            for(c = 0; c < alphabetSize; c++){
                if (compareUnsigned( sumProbs[c] + probs[c], threshold) > 0) break;
            }

            message[i] = c;

            int oldLeft = left;
            left = ( int ) (oldLeft + sumProbs[c] * (range) / totalCount );
            right = ( int ) (oldLeft + (sumProbs[c] + probs[c]) * (range) / totalCount - 1);

            // Normalize if need
            while (true){
                if(compareUnsigned( right, half - 1) <= 0){
                } else if (compareUnsigned( left , half) >= 0) {
                    value -= half;
                    left -= half;
                    right -= half;
                } else if (compareUnsigned( left , firstQtr) >= 0 && compareUnsigned( right , thirdQtr - 1) <= 0) {
                    //value -= qtr;
                    //left -= qtr;
                    //right -= qtr;
                    right = half - 1;
                    continue;
                } else break;
                left += left;
                right += right + 1;
                value <<= 1;
                value += readBit( inputStream );
                value &= valueMask;
            }
        }

        return message;
    }
}
