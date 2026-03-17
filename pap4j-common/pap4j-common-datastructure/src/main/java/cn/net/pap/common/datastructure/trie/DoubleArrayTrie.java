/**
 * <p><strong>DoubleArrayTrie</strong> 是 Darts (Double-ARray Trie System) 的 Java 实现。</p>
 *
 * <p>它提供了一种高效的字符串字典数据结构，允许快速的精确匹配和公共前缀搜索。</p>
 *
 * <p>
 * Copyright(C) 2001-2007 Taku Kudo &lt;taku@chasen.org&gt;<br />
 * Copyright(C) 2009 MURAWAKI Yugo &lt;murawaki@nlp.kuee.kyoto-u.ac.jp&gt;<br />
 * Copyright(C) 2012 KOMIYA Atsushi &lt;komiya.atsushi@gmail.com&gt;
 * </p>
 *
 * <p>
 * 本文件内容可在 GNU 宽通用公共许可证 2.1 版或更高版本 ("LGPL") 或 BSD 许可证 ("BSD") 的条款下使用。
 * </p>
 */
package cn.net.pap.common.datastructure.trie;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class DoubleArrayTrie {
    private final static int BUF_SIZE = 16384;
    private final static int UNIT_SIZE = 8; // size of int + int

    private static class Node {
        int code;
        int depth;
        int left;
        int right;
    }

    ;

    private int check[];
    private int base[];

    private boolean used[];
    private int size;
    private int allocSize;
    private List<String> key;
    private int keySize;
    private int length[];
    private int value[];
    private int progress;
    private int nextCheckPos;
    // boolean no_delete_;
    int error_;

    // int (*progressfunc_) (size_t, size_t);

    // inline _resize expanded
    private int resize(int newSize) {
        int[] base2 = new int[newSize];
        int[] check2 = new int[newSize];
        boolean used2[] = new boolean[newSize];
        if (allocSize > 0) {
            System.arraycopy(base, 0, base2, 0, allocSize);
            System.arraycopy(check, 0, check2, 0, allocSize);
            System.arraycopy(used2, 0, used2, 0, allocSize);
        }

        base = base2;
        check = check2;
        used = used2;

        return allocSize = newSize;
    }

    private int fetch(Node parent, List<Node> siblings) {
        if (error_ < 0)
            return 0;

        int prev = 0;

        for (int i = parent.left; i < parent.right; i++) {
            if ((length != null ? length[i] : key.get(i).length()) < parent.depth)
                continue;

            String tmp = key.get(i);

            int cur = 0;
            if ((length != null ? length[i] : tmp.length()) != parent.depth)
                cur = (int) tmp.charAt(parent.depth) + 1;

            if (prev > cur) {
                error_ = -3;
                return 0;
            }

            if (cur != prev || siblings.size() == 0) {
                Node tmp_node = new Node();
                tmp_node.depth = parent.depth + 1;
                tmp_node.code = cur;
                tmp_node.left = i;
                if (siblings.size() != 0)
                    siblings.get(siblings.size() - 1).right = i;

                siblings.add(tmp_node);
            }

            prev = cur;
        }

        if (siblings.size() != 0)
            siblings.get(siblings.size() - 1).right = parent.right;

        return siblings.size();
    }

    private int insert(List<Node> siblings) {
        if (error_ < 0)
            return 0;

        int begin = 0;
        int pos = ((siblings.get(0).code + 1 > nextCheckPos) ? siblings.get(0).code + 1
                : nextCheckPos) - 1;
        int nonzero_num = 0;
        int first = 0;

        if (allocSize <= pos)
            resize(pos + 1);

        outer:
        while (true) {
            pos++;

            if (allocSize <= pos)
                resize(pos + 1);

            if (check[pos] != 0) {
                nonzero_num++;
                continue;
            } else if (first == 0) {
                nextCheckPos = pos;
                first = 1;
            }

            begin = pos - siblings.get(0).code;
            if (allocSize <= (begin + siblings.get(siblings.size() - 1).code)) {
                // progress can be zero
                double l = (1.05 > 1.0 * keySize / (progress + 1)) ? 1.05 : 1.0
                        * keySize / (progress + 1);
                resize((int) (allocSize * l));
            }

            if (used[begin])
                continue;

            for (int i = 1; i < siblings.size(); i++)
                if (check[begin + siblings.get(i).code] != 0)
                    continue outer;

            break;
        }

        // -- Simple heuristics --
        // if the percentage of non-empty contents in check between the
        // index
        // 'next_check_pos' and 'check' is greater than some constant value
        // (e.g. 0.9),
        // new 'next_check_pos' index is written by 'check'.
        if (1.0 * nonzero_num / (pos - nextCheckPos + 1) >= 0.95)
            nextCheckPos = pos;

        used[begin] = true;
        size = (size > begin + siblings.get(siblings.size() - 1).code + 1) ? size
                : begin + siblings.get(siblings.size() - 1).code + 1;

        for (int i = 0; i < siblings.size(); i++)
            check[begin + siblings.get(i).code] = begin;

        for (int i = 0; i < siblings.size(); i++) {
            List<Node> new_siblings = new ArrayList<Node>();

            if (fetch(siblings.get(i), new_siblings) == 0) {
                base[begin + siblings.get(i).code] = (value != null) ? (-value[siblings
                        .get(i).left] - 1) : (-siblings.get(i).left - 1);

                if (value != null && (-value[siblings.get(i).left] - 1) >= 0) {
                    error_ = -2;
                    return 0;
                }

                progress++;
                // if (progress_func_) (*progress_func_) (progress,
                // keySize);
            } else {
                int h = insert(new_siblings);
                base[begin + siblings.get(i).code] = h;
            }
        }
        return begin;
    }

    /**
     * <p>构造一个全新的未初始化的双数组字典树实例。</p>
     */
    public DoubleArrayTrie() {
        check = null;
        base = null;
        used = null;
        size = 0;
        allocSize = 0;
        // no_delete_ = false;
        error_ = 0;
    }

    // no deconstructor

    // set_result omitted
    // the search methods returns (the list of) the value(s) instead
    // of (the list of) the pair(s) of value(s) and length(s)

    // set_array omitted
    // array omitted

    void clear() {
        // if (! no_delete_)
        check = null;
        base = null;
        used = null;
        allocSize = 0;
        size = 0;
        // no_delete_ = false;
    }

    /**
     * <p>获取每个节点的单位大小（字节）。</p>
     * @return 单位大小。
     */
    public int getUnitSize() {
        return UNIT_SIZE;
    }

    /**
     * <p>获取数组中当前分配的项目数。</p>
     * @return 数组的大小。
     */
    public int getSize() {
        return size;
    }

    /**
     * <p>获取数组占用的总大小（以字节为单位）。</p>
     * @return 总大小（字节）。
     */
    public int getTotalSize() {
        return size * UNIT_SIZE;
    }

    /**
     * <p>获取 check 数组中非零条目的总数。</p>
     * @return 非零 check 计数。
     */
    public int getNonzeroSize() {
        int result = 0;
        for (int i = 0; i < size; i++)
            if (check[i] != 0)
                result++;
        return result;
    }

    /**
     * <p>使用按词法排序的字符串键列表构建字典树结构。</p>
     *
     * @param key 排序后的键列表。
     * @return 错误码（0 表示成功）。
     */
    public int build(List<String> key) {
        return build(key, null, null, key.size());
    }

    /**
     * <p>构建指定自定义长度和值的字典树结构。</p>
     *
     * @param _key     排序后的字符串键列表。
     * @param _length  键长度的平行数组（可为 null）。
     * @param _value   分配给每个键的值的平行数组（可为 null）。
     * @param _keySize 要索引的有效键的总数。
     * @return 错误码（0 表示成功）。
     */
    public int build(List<String> _key, int _length[], int _value[],
                     int _keySize) {
        if (_keySize > _key.size() || _key == null)
            return 0;

        // progress_func_ = progress_func;
        key = _key;
        length = _length;
        keySize = _keySize;
        value = _value;
        progress = 0;

        resize(65536 * 32);

        base[0] = 1;
        nextCheckPos = 0;

        Node root_node = new Node();
        root_node.left = 0;
        root_node.right = keySize;
        root_node.depth = 0;

        List<Node> siblings = new ArrayList<Node>();
        fetch(root_node, siblings);
        insert(siblings);

        // size += (1 << 8 * 2) + 1; // ???
        // if (size >= allocSize) resize (size);

        used = null;
        key = null;

        return error_;
    }

    /**
     * <p>从外部文件打开字典树。</p>
     *
     * @param fileName 文件路径。
     * @throws IOException 如果文件读取失败。
     */
    public void open(String fileName) throws IOException {
        File file = new File(fileName);
        size = (int) file.length() / UNIT_SIZE;
        check = new int[size];
        base = new int[size];

        DataInputStream is = null;
        try {
            is = new DataInputStream(new BufferedInputStream(
                    new FileInputStream(file), BUF_SIZE));
            for (int i = 0; i < size; i++) {
                base[i] = is.readInt();
                check[i] = is.readInt();
            }
        } finally {
            if (is != null)
                is.close();
        }
    }

    /**
     * <p>将当前的字典树状态保存到外部文件。</p>
     *
     * @param fileName 保存文件的路径。
     * @throws IOException 如果文件写入失败。
     */
    public void save(String fileName) throws IOException {
        DataOutputStream out = null;
        try {
            out = new DataOutputStream(new BufferedOutputStream(
                    new FileOutputStream(fileName)));
            for (int i = 0; i < size; i++) {
                out.writeInt(base[i]);
                out.writeInt(check[i]);
            }
            out.close();
        } finally {
            if (out != null)
                out.close();
        }
    }

    /**
     * <p>执行字符串键的精确匹配搜索。</p>
     *
     * @param key 要查找的键。
     * @return 与键关联的整数值，如果未找到则返回 -1。
     */
    public int exactMatchSearch(String key) {
        return exactMatchSearch(key, 0, 0, 0);
    }

    /**
     * <p>指定搜索边界执行精确匹配搜索。</p>
     *
     * @param key     要查找的键。
     * @param pos     起始字符位置。
     * @param len     要计算的键切片长度。
     * @param nodePos 根节点偏移索引。
     * @return 整数值，如果未找到则返回 -1。
     */
    public int exactMatchSearch(String key, int pos, int len, int nodePos) {
        if (len <= 0)
            len = key.length();
        if (nodePos <= 0)
            nodePos = 0;

        int result = -1;

        char[] keyChars = key.toCharArray();

        int b = base[nodePos];
        int p;

        for (int i = pos; i < len; i++) {
            p = b + (int) (keyChars[i]) + 1;
            if (b == check[p])
                b = base[p];
            else
                return result;
        }

        p = b;
        int n = base[p];
        if (b == check[p] && n < 0) {
            result = -n - 1;
        }
        return result;
    }

    /**
     * <p>执行公共前缀搜索，发现构成输入字符串前缀的所有字典匹配项。</p>
     *
     * @param key 目标字符串。
     * @return 匹配序列值的 {@link List}。
     */
    public List<Integer> commonPrefixSearch(String key) {
        return commonPrefixSearch(key, 0, 0, 0);
    }

    /**
     * <p>指定搜索边界执行公共前缀搜索。</p>
     *
     * @param key     目标字符串。
     * @param pos     起始字符位置。
     * @param len     输入切片的长度。
     * @param nodePos 根节点偏移索引。
     * @return 匹配序列值的 {@link List}。
     */
    public List<Integer> commonPrefixSearch(String key, int pos, int len,
                                            int nodePos) {
        if (len <= 0)
            len = key.length();
        if (nodePos <= 0)
            nodePos = 0;

        List<Integer> result = new ArrayList<Integer>();

        char[] keyChars = key.toCharArray();

        int b = base[nodePos];
        int n;
        int p;

        for (int i = pos; i < len; i++) {
            p = b;
            n = base[p];

            if (b == check[p] && n < 0) {
                result.add(-n - 1);
            }

            p = b + (int) (keyChars[i]) + 1;
            if (b == check[p])
                b = base[p];
            else
                return result;
        }

        p = b;
        n = base[p];

        if (b == check[p] && n < 0) {
            result.add(-n - 1);
        }

        return result;
    }

    /**
     * <p>将内部数组转储到标准错误流以进行调试。</p>
     */
    public void dump() {
        for (int i = 0; i < size; i++) {
            System.err.println("i: " + i + " [" + base[i] + ", " + check[i]
                    + "]");
        }
    }
}