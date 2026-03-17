package cn.net.pap.common.datastructure.frequency;

/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.*;
import java.util.Arrays;
import java.util.Random;

/**
 * <h1>Count-Min Sketch 频率估算数据结构</h1>
 * <p>基于哈希散列的数据流概要统计工具，主要用于极低的内存开销下快速估计大批量数据流中特定元素的出现频率。</p>
 * <p>
 * <strong>参考论文:</strong> <br>
 * An Improved Data Stream Summary: The Count-Min Sketch and its Applications<br>
 * <a href="https://web.archive.org/web/20060907232042/http://www.eecs.harvard.edu/~michaelm/CS222/countmin.pdf">https://web.archive.org/web/20060907232042/http://www.eecs.harvard.edu/~michaelm/CS222/countmin.pdf</a>
 * </p>
 * 
 * @author alexgaoyh
 */
public class CountMinSketch implements Serializable {

    /**
     * <p>素数取模数基数常量，用于快速散列计算（即 2^31 - 1）。</p>
     */
    public static final long PRIME_MODULUS = (1L << 31) - 1;
    
    private static final long serialVersionUID = -5084982213094657923L;

    /** <p>二维计数表中的哈希函数数量（行数）。</p> */
    int depth;
    /** <p>二维计数表中每个哈希函数的桶数（列数）。</p> */
    int width;
    /** <p>存放元素计数估值的二维表。</p> */
    long[][] table;
    /** <p>记录对应每一层散列函数生成计算用到的随机系数。</p> */
    long[] hashA;
    /** <p>统计加入当前草图中的总元素数量。</p> */
    long size;
    /** <p>允许的相对误差范围（epsilon）。</p> */
    double eps;
    /** <p>估算准确率概率置信度。</p> */
    double confidence;

    /**
     * <p>默认无参构造函数。</p>
     */
    CountMinSketch() {
    }

    /**
     * <p>通过直接指定行数与列数构造 CountMinSketch。</p>
     *
     * @param depth 行数（哈希函数的数量）
     * @param width 列数（散列桶的宽度）
     * @param seed  初始化散列函数的随机种子
     */
    public CountMinSketch(int depth, int width, int seed) {
        this.depth = depth;
        this.width = width;
        this.eps = 2.0 / width;
        this.confidence = 1 - 1 / Math.pow(2, depth);
        initTablesWith(depth, width, seed);
    }

    /**
     * <p>通过指定容忍误差和置信度构造 CountMinSketch。</p>
     * <p>系统会自动推算出最优的 {@code depth} 和 {@code width} 参数。</p>
     *
     * @param epsOfTotalCount 允许相对于总数的频率估算误差值（如 0.001）
     * @param confidence      置信概率（如 0.99，表示 99% 的概率估算在误差范围内）
     * @param seed            初始化散列函数的随机种子
     */
    public CountMinSketch(double epsOfTotalCount, double confidence, int seed) {
        // 2/w = eps ; w = 2/eps
        // 1/2^depth <= 1-confidence ; depth >= -log2 (1-confidence)
        this.eps = epsOfTotalCount;
        this.confidence = confidence;
        this.width = (int) Math.ceil(2 / epsOfTotalCount);
        this.depth = (int) Math.ceil(-Math.log(1 - confidence) / Math.log(2));
        initTablesWith(depth, width, seed);
    }

    /**
     * <p>基于已有状态直接还原和构造的内部方法。</p>
     * 
     * @param depth 深度
     * @param width 宽度
     * @param size  总大小
     * @param hashA 哈希随机系数
     * @param table 计数的二维表
     */
    CountMinSketch(int depth, int width, long size, long[] hashA, long[][] table) {
        this.depth = depth;
        this.width = width;
        this.eps = 2.0 / width;
        this.confidence = 1 - 1 / Math.pow(2, depth);
        this.hashA = hashA;
        this.table = table;

        this.size = size;
    }

    @Override
    public String toString() {
        return "CountMinSketch{" +
                "eps=" + eps +
                ", confidence=" + confidence +
                ", depth=" + depth +
                ", width=" + width +
                ", size=" + size +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final CountMinSketch that = (CountMinSketch) o;

        if (depth != that.depth) {
            return false;
        }
        if (width != that.width) {
            return false;
        }

        if (Double.compare(that.eps, eps) != 0) {
            return false;
        }
        if (Double.compare(that.confidence, confidence) != 0) {
            return false;
        }

        if (size != that.size) {
            return false;
        }

        if (!Arrays.deepEquals(table, that.table)) {
            return false;
        }
        return Arrays.equals(hashA, that.hashA);
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = depth;
        result = 31 * result + width;
        result = 31 * result + Arrays.deepHashCode(table);
        result = 31 * result + Arrays.hashCode(hashA);
        result = 31 * result + (int) (size ^ (size >>> 32));
        temp = Double.doubleToLongBits(eps);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(confidence);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    /**
     * <p>初始化内部哈希系数及二维计分板数组。</p>
     *
     * @param depth 深度
     * @param width 宽度
     * @param seed 随机种子
     */
    private void initTablesWith(int depth, int width, int seed) {
        this.table = new long[depth][width];
        this.hashA = new long[depth];
        Random r = new Random(seed);
        // 我们使用线性哈希函数: (a*x+b) mod p。由于 b 不影响均匀性可只随机 a
        for (int i = 0; i < depth; ++i) {
            hashA[i] = r.nextInt(Integer.MAX_VALUE);
        }
    }

    /**
     * <p>获取该数据结构的理论相对误差值。</p>
     *
     * @return 相对误差（epsilon）
     */
    public double getRelativeError() {
        return eps;
    }

    /**
     * <p>获取该数据结构的统计置信度概率。</p>
     *
     * @return 置信度概率
     */
    public double getConfidence() {
        return confidence;
    }

    /**
     * <p>为 {@code long} 类型元素生成它在第 {@code i} 层的哈希桶索引位置。</p>
     *
     * @param item 待散列的数字型元素
     * @param i 所在的哈希层（函数编号）
     * @return 第 {@code i} 层的列下标
     */
    int hash(long item, int i) {
        long hash = hashA[i] * item;
        // 非常快速地计算 x mod (2^31 - 1) 的算法
        hash += hash >> 32;
        hash &= PRIME_MODULUS;
        // Doing "%" after (int) conversion is ~2x faster than %'ing longs.
        return ((int) hash) % width;
    }

    /**
     * <p>安全检查防止记录总数溢出 {@code Long.MAX_VALUE}。</p>
     *
     * @param previousSize 修改前的规模
     * @param operation    进行的操作类型标识
     * @param newSize      修改后的规模
     */
    private static void checkSizeAfterOperation(long previousSize, String operation, long newSize) {
        if (newSize < previousSize) {
            throw new IllegalStateException("Overflow error: the size after calling `" + operation +
                    "` is smaller than the previous size. " +
                    "Previous size: " + previousSize +
                    ", New size: " + newSize);
        }
    }

    private void checkSizeAfterAdd(String item, long count) {
        long previousSize = size;
        size += count;
        checkSizeAfterOperation(previousSize, "add(" + item + "," + count + ")", size);
    }

    /**
     * <p>向估计器中增量更新一个数值型元素的频率。</p>
     *
     * @param item  具体的 long 元素项
     * @param count 要增加的频数，必须大于等于 0
     * @throws IllegalArgumentException 如果 {@code count} 是负数
     */
    public void add(long item, long count) {
        if (count < 0) {
            // Actually for negative increments we'll need to use the median
            // instead of minimum, and accuracy will suffer somewhat.
            // Probably makes sense to add an "allow negative increments"
            // parameter to constructor.
            throw new IllegalArgumentException("Negative increments not implemented");
        }
        for (int i = 0; i < depth; ++i) {
            table[i][hash(item, i)] += count;
        }

        checkSizeAfterAdd(String.valueOf(item), count);
    }

    /**
     * <p>向估计器中增量更新一个字符串类型元素的频率。</p>
     *
     * @param item  具体的 String 元素项
     * @param count 要增加的频数，必须大于等于 0
     * @throws IllegalArgumentException 如果 {@code count} 是负数
     */
    public void add(String item, long count) {
        if (count < 0) {
            // Actually for negative increments we'll need to use the median
            // instead of minimum, and accuracy will suffer somewhat.
            // Probably makes sense to add an "allow negative increments"
            // parameter to constructor.
            throw new IllegalArgumentException("Negative increments not implemented");
        }
        int[] buckets = getHashBuckets(item, depth, width);
        for (int i = 0; i < depth; ++i) {
            table[i][buckets[i]] += count;
        }

        checkSizeAfterAdd(item, count);
    }

    /**
     * <p>获取输入到这个 Sketch 中的全体总频数之和。</p>
     *
     * @return 元素的总累加次数
     */
    public long size() {
        return size;
    }

    /**
     * <p>估计指定 {@code long} 元素的历史发生频率。</p>
     * <p>估算值保证会超出或等于实际值。该结果被超出 {@code 误差量 * 总记录数} 的概率由 {@code 置信度} 保证。</p>
     *
     * @param item 待查询的元素
     * @return 估算所得的发生频率
     */
    public long estimateCount(long item) {
        long res = Long.MAX_VALUE;
        for (int i = 0; i < depth; ++i) {
            res = Math.min(res, table[i][hash(item, i)]);
        }
        return res;
    }

    /**
     * <p>估计指定 {@code String} 元素的历史发生频率。</p>
     *
     * @param item 待查询的元素
     * @return 估算所得的发生频率
     */
    public long estimateCount(String item) {
        long res = Long.MAX_VALUE;
        int[] buckets = getHashBuckets(item, depth, width);
        for (int i = 0; i < depth; ++i) {
            res = Math.min(res, table[i][buckets[i]]);
        }
        return res;
    }

    /**
     * <p>将多个结构配置兼容相同的 CountMinSketch 数据流合并为一个新的汇总估计器。</p>
     *
     * @param estimators 待合并的估计器数组
     * @return 合并完成后的新估计器对象；如果没有传入参数则返回 {@code null}
     * @throws CMSMergeException 如果待合并的实例在 {@code depth}、{@code width} 或随机种子上不一致而无法合并
     */
    public static CountMinSketch merge(CountMinSketch... estimators) throws CMSMergeException {
        CountMinSketch merged = null;
        if (estimators != null && estimators.length > 0) {
            int depth = estimators[0].depth;
            int width = estimators[0].width;
            long[] hashA = Arrays.copyOf(estimators[0].hashA, estimators[0].hashA.length);

            long[][] table = new long[depth][width];
            long size = 0;

            for (CountMinSketch estimator : estimators) {
                if (estimator.depth != depth) {
                    throw new CMSMergeException("Cannot merge estimators of different depth");
                }
                if (estimator.width != width) {
                    throw new CMSMergeException("Cannot merge estimators of different width");
                }
                if (!Arrays.equals(estimator.hashA, hashA)) {
                    throw new CMSMergeException("Cannot merge estimators of different seed");
                }

                for (int i = 0; i < table.length; i++) {
                    for (int j = 0; j < table[i].length; j++) {
                        table[i][j] += estimator.table[i][j];
                    }
                }

                long previousSize = size;
                size += estimator.size;
                checkSizeAfterOperation(previousSize, "merge(" + estimator + ")", size);
            }

            merged = new CountMinSketch(depth, width, size, hashA, table);
        }

        return merged;
    }

    /**
     * <p>将估计器对象状态序列化转换为原生的 {@code byte[]} 字节数组。</p>
     *
     * @param sketch 需要序列化的 CountMinSketch 对象
     * @return 序列化后的字节数组
     */
    public static byte[] serialize(CountMinSketch sketch) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream s = new DataOutputStream(bos);
        try {
            s.writeLong(sketch.size);
            s.writeInt(sketch.depth);
            s.writeInt(sketch.width);
            for (int i = 0; i < sketch.depth; ++i) {
                s.writeLong(sketch.hashA[i]);
                for (int j = 0; j < sketch.width; ++j) {
                    s.writeLong(sketch.table[i][j]);
                }
            }
            s.close();
            return bos.toByteArray();
        } catch (IOException e) {
            // Shouldn't happen
            throw new RuntimeException(e);
        }
    }

    /**
     * <p>从序列化好的 {@code byte[]} 字节数组中反序列化还原 CountMinSketch 对象实例。</p>
     *
     * @param data 包含序列化数据的字节数组
     * @return 反序列化还原出的 CountMinSketch 实例
     */
    public static CountMinSketch deserialize(byte[] data) {
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        DataInputStream s = new DataInputStream(bis);
        try {
            CountMinSketch sketch = new CountMinSketch();
            sketch.size = s.readLong();
            sketch.depth = s.readInt();
            sketch.width = s.readInt();
            sketch.eps = 2.0 / sketch.width;
            sketch.confidence = 1 - 1 / Math.pow(2, sketch.depth);
            sketch.hashA = new long[sketch.depth];
            sketch.table = new long[sketch.depth][sketch.width];
            for (int i = 0; i < sketch.depth; ++i) {
                sketch.hashA[i] = s.readLong();
                for (int j = 0; j < sketch.width; ++j) {
                    sketch.table[i][j] = s.readLong();
                }
            }
            return sketch;
        } catch (IOException e) {
            // Shouldn't happen
            throw new RuntimeException(e);
        }
    }

    /**
     * <p>内部定义的自定义异常，当 Count-Min Sketch 在合并操作发生由于属性不兼容导致的错误时被抛出。</p>
     */
    @SuppressWarnings("serial")
    protected static class CMSMergeException extends Exception {
        public CMSMergeException(String message) {
            super(message);
        }
    }

    /**
     * <p>使用更高效且满足组合独立性假设的衍生方法来计算字符串在不同深度的哈希桶索引位置。</p>
     * <p>该实现使用了双 MurmurHash 派生法，这比直接对每一层使用不同的独立哈希函数速度更快。</p>
     *
     * @param key       需要取哈希的字符串
     * @param hashCount 需要生成的不同哈希值的个数（等于层数 depth）
     * @param max       哈希输出的取模界限上限（等于桶宽 width）
     * @return 返回长度等于 {@code hashCount} 的结果数组
     */
    public static int[] getHashBuckets(String key, int hashCount, int max) {
        byte[] b;
        try {
            b = key.getBytes("UTF-16");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        return getHashBuckets(b, hashCount, max);
    }


    /**
     * <p>基于给定的字节数组通过衍生算法计算包含 {@code hashCount} 个独立的哈希桶索引数组。</p>
     *
     * @param b 待散列字节数组
     * @param hashCount 生成数量
     * @param max 最大哈希范围值
     * @return 散列位置数组
     */
    static int[] getHashBuckets(byte[] b, int hashCount, int max) {
        int[] result = new int[hashCount];
        int hash1 = MurmurHash.hash(b, b.length, 0);
        int hash2 = MurmurHash.hash(b, b.length, hash1);
        for (int i = 0; i < hashCount; i++) {
            result[i] = Math.abs((hash1 + i * hash2) % max);
        }
        return result;
    }
}