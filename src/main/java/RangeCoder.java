import java.io.ByteArrayOutputStream;

/**
 * @author igor.kostromin
 *         22.05.2014 16:49
 */
public class RangeCoder {
    private final int alphabetSize;
    private final int precision = 16;
    private final int[] probs;

    // precision - степень двойки, от 2 до 32
    // размер алфавита <= 2^(precision-2) (минимум по точке на символ в 1/4 интервала)
    public RangeCoder(int alphabetSize){
        // todo :
        //assert !(precision < 9 || precision > 32);
        //assert alphabetSize <= 1 << 8;
        this.alphabetSize = alphabetSize;
        this.probs = new int[alphabetSize];
        //this.precision = precision;
    }

    // считает rawProbs и преобразует в probs, пригодные для кодирования
    // на выходе должны быть probs, в котором нет ни одного нулевого элемента,
    // а сумма всех значений не превышает 2^8
    public void count(int[] message){
        final int totalCountTreshold = 1 << 8;
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

        return null;
    }
}
