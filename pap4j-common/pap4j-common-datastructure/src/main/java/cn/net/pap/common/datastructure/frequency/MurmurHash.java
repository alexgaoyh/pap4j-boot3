package cn.net.pap.common.datastructure.frequency;

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

/**
 * <h1>MurmurHash (非加密高速散列算法)</h1>
 * <p>提供极其高速且具有极强雪崩效应的散列算法实现，非常适合作为普通的 HashTable 键散列或大规模数据快速指纹计算。</p>
 * <p>
 * 关于更多理论细节，可以参考: <a href="http://murmurhash.googlepages.com/">http://murmurhash.googlepages.com/</a>
 * </p>
 * <p>
 * <strong>注意：</strong>该实现并不是用于安全的密码学哈希，因此不要应用于验证数据安全性签名等密码场景。
 * </p>
 * 
 * @author alexgaoyh
 */
public class MurmurHash {

    /**
     * <p>对通用对象求出其相应的 32 位 MurmurHash 整型数值。</p>
     * <p>根据对象类型，会自动分配到底层的 Long / Integer / Double / Float / String / byte[] 专用方法进行散列。</p>
     *
     * @param o 待求哈希的任意对象
     * @return 计算后的 32 位哈希整数
     */
    public static int hash(Object o) {
        if (o == null) {
            return 0;
        }
        if (o instanceof Long) {
            return hashLong((Long) o);
        }
        if (o instanceof Integer) {
            return hashLong((Integer) o);
        }
        if (o instanceof Double) {
            return hashLong(Double.doubleToRawLongBits((Double) o));
        }
        if (o instanceof Float) {
            return hashLong(Float.floatToRawIntBits((Float) o));
        }
        if (o instanceof String) {
            return hash(((String) o).getBytes());
        }
        if (o instanceof byte[]) {
            return hash((byte[]) o);
        }
        return hash(o.toString());
    }

    /**
     * <p>基于给定的字节数组生成 32 位的 MurmurHash 值，默认使用 {@code -1} 的种子。</p>
     *
     * @param data 输入的字节数组
     * @return 计算后的 32 位散列值
     */
    public static int hash(byte[] data) {
        return hash(data, data.length, -1);
    }

    /**
     * <p>指定哈希种子并基于给定的字节数组生成 32 位的 MurmurHash 值。</p>
     *
     * @param data 输入的字节数组
     * @param seed 初始的随机种子整型
     * @return 计算后的 32 位散列值
     */
    public static int hash(byte[] data, int seed) {
        return hash(data, data.length, seed);
    }

    /**
     * <p>指定有效长度及种子，基于字节数组进行 32 位 MurmurHash 的最终底层算法实现。</p>
     *
     * @param data   字节数组数据源
     * @param length 要处理的字节最大长度
     * @param seed   散列函数的初始化参数值
     * @return 经过多步变换生成的 32 位散列值
     */
    public static int hash(byte[] data, int length, int seed) {
        int m = 0x5bd1e995;
        int r = 24;

        int h = seed ^ length;

        int len_4 = length >> 2;

        for (int i = 0; i < len_4; i++) {
            int i_4 = i << 2;
            int k = data[i_4 + 3];
            k = k << 8;
            k = k | (data[i_4 + 2] & 0xff);
            k = k << 8;
            k = k | (data[i_4 + 1] & 0xff);
            k = k << 8;
            k = k | (data[i_4 + 0] & 0xff);
            k *= m;
            k ^= k >>> r;
            k *= m;
            h *= m;
            h ^= k;
        }

        // 处理最后剩余的尾部非 4 的整数倍部分的字节
        int len_m = len_4 << 2;
        int left = length - len_m;

        if (left != 0) {
            if (left >= 3) {
                h ^= (int) data[length - 3] << 16;
            }
            if (left >= 2) {
                h ^= (int) data[length - 2] << 8;
            }
            if (left >= 1) {
                h ^= (int) data[length - 1];
            }

            h *= m;
        }

        // 最终的位混合，确保更好的雪崩效应
        h ^= h >>> 13;
        h *= m;
        h ^= h >>> 15;

        return h;
    }

    /**
     * <p>直接对给定的 {@code long} 标量生成其 32 位的 MurmurHash 值，效率优于转换为数组。</p>
     *
     * @param data 待计算的长整型值
     * @return 32 位哈希整数
     */
    public static int hashLong(long data) {
        int m = 0x5bd1e995;
        int r = 24;

        int h = 0;

        int k = (int) data * m;
        k ^= k >>> r;
        h ^= k * m;

        k = (int) (data >> 32) * m;
        k ^= k >>> r;
        h *= m;
        h ^= k * m;

        h ^= h >>> 13;
        h *= m;
        h ^= h >>> 15;

        return h;
    }

    /**
     * <p>针对对象的通用重载：生成 64 位的 MurmurHash 值。</p>
     *
     * @param o 待求散列的对象
     * @return 64位哈希长整型数
     */
    public static long hash64(Object o) {
        if (o == null) {
            return 0l;
        } else if (o instanceof String) {
            final byte[] bytes = ((String) o).getBytes();
            return hash64(bytes, bytes.length);
        } else if (o instanceof byte[]) {
            final byte[] bytes = (byte[]) o;
            return hash64(bytes, bytes.length);
        }
        return hash64(o.toString());
    }

    /**
     * <p>基于给定的字节数组使用系统默认种子 {@code 0xe17a1465} 计算出 64 位的哈希值。</p>
     *
     * @param data   代求哈希字节数组
     * @param length 需要参与运算的数组长度
     * @return 64 位的哈希长整型数
     */
    public static long hash64(final byte[] data, int length) {
        return hash64(data, length, 0xe17a1465);
    }

    /**
     * <p>带指定种子的底层 64 位版本 MurmurHash 具体实现。</p>
     * <p>一次处理 8 个字节的块并在末端完成混合。</p>
     *
     * @param data   字节数组
     * @param length 数组的参与散列长度
     * @param seed   哈希随机种子整型
     * @return 完成最终混合处理的 64 位散列值
     */
    public static long hash64(final byte[] data, int length, int seed) {
        final long m = 0xc6a4a7935bd1e995L;
        final int r = 47;

        long h = (seed & 0xffffffffl) ^ (length * m);

        int length8 = length / 8;

        for (int i = 0; i < length8; i++) {
            final int i8 = i * 8;
            long k = ((long) data[i8 + 0] & 0xff) + (((long) data[i8 + 1] & 0xff) << 8) + (((long) data[i8 + 2] & 0xff) << 16) + (((long) data[i8 + 3] & 0xff) << 24) + (((long) data[i8 + 4] & 0xff) << 32) + (((long) data[i8 + 5] & 0xff) << 40) + (((long) data[i8 + 6] & 0xff) << 48) + (((long) data[i8 + 7] & 0xff) << 56);

            k *= m;
            k ^= k >>> r;
            k *= m;

            h ^= k;
            h *= m;
        }

        switch (length % 8) {
            case 7:
                h ^= (long) (data[(length & ~7) + 6] & 0xff) << 48;
            case 6:
                h ^= (long) (data[(length & ~7) + 5] & 0xff) << 40;
            case 5:
                h ^= (long) (data[(length & ~7) + 4] & 0xff) << 32;
            case 4:
                h ^= (long) (data[(length & ~7) + 3] & 0xff) << 24;
            case 3:
                h ^= (long) (data[(length & ~7) + 2] & 0xff) << 16;
            case 2:
                h ^= (long) (data[(length & ~7) + 1] & 0xff) << 8;
            case 1:
                h ^= (long) (data[length & ~7] & 0xff);
                h *= m;
        }
        ;

        h ^= h >>> r;
        h *= m;
        h ^= h >>> r;

        return h;
    }
}
