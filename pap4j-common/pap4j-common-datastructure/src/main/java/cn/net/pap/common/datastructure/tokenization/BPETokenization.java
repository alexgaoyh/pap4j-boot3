package cn.net.pap.common.datastructure.tokenization;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p><strong>BPETokenization</strong> 实现了字节对编码 (BPE) 算法。</p>
 *
 * <p>BPE 是一种简单的数据压缩形式，其中最常见的连续数据字节对被替换为不出现在该数据中的字节。在自然语言处理中，它用于子词分词。</p>
 *
 * <ul>
 *     <li>从给定的字符串列表训练词表。</li>
 *     <li>使用训练好的词表对新的文本字符串进行分词。</li>
 *     <li>采用贪心最长匹配方法进行分词。</li>
 * </ul>
 */
public class BPETokenization {

    /**
     * <p>由子词和字符组成的已学习词表。</p>
     */
    private Set<String> vocab = new HashSet<>();

    /**
     * <p>训练过程中维护的内部序列。</p>
     */
    private List<List<String>> sequences = new ArrayList<>();

    /**
     * <p><strong>Pair</strong> 是一个简单的元组，用于跟踪相邻的符号。</p>
     *
     * @param <K> 第一个元素的类型。
     * @param <V> 第二个元素的类型。
     */
    static class Pair<K, V> {
        K first;
        V second;

        Pair(K first, V second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Pair<?, ?> pair = (Pair<?, ?>) o;
            return Objects.equals(first, pair.first) && Objects.equals(second, pair.second);
        }

        @Override
        public int hashCode() {
            return Objects.hash(first, second);
        }
    }

    /**
     * <p>在输入词列表上训练 BPE 模型。</p>
     *
     * <p>此方法迭代查找最频繁的相邻符号对，并将它们合并为新符号，将其添加到词表中，直到没有出现次数大于一的符号对。</p>
     *
     * @param words 用于训练的字符串 {@link List}。
     */
    public void train(List<String> words) {
        // 初始化符号序列和词表
        sequences = words.stream().map(word -> word.chars().mapToObj(c -> String.valueOf((char) c)).collect(Collectors.toList())).collect(Collectors.toList());

        vocab.clear();
        sequences.forEach(vocab::addAll);

        // 循环合并
        while (true) {
            // 统计相邻符号对频率
            Map<Pair<String, String>, Integer> freq = new HashMap<>();
            for (List<String> seq : sequences) {
                for (int i = 0; i < seq.size() - 1; i++) {
                    Pair<String, String> pair = new Pair<>(seq.get(i), seq.get(i + 1));
                    freq.put(pair, freq.getOrDefault(pair, 0) + 1);
                }
            }

            if (freq.isEmpty()) break;

            // 找出最高频的符号对
            Pair<String, String> bestPair = Collections.max(freq.entrySet(), Map.Entry.comparingByValue()).getKey();
            if (freq.get(bestPair) < 2) break;

            // 创建合并后的新符号
            String merged = bestPair.first + bestPair.second;
            vocab.add(merged);

            // 在所有序列中替换符号对
            for (List<String> seq : sequences) {
                for (int i = 0; i < seq.size() - 1; ) {
                    if (seq.get(i).equals(bestPair.first) && seq.get(i + 1).equals(bestPair.second)) {
                        seq.set(i, merged);
                        seq.remove(i + 1);
                    } else {
                        i++;
                    }
                }
            }
        }
    }

    /**
     * <p>基于训练好的词表将输入文本字符串分词为子词。</p>
     *
     * <p>使用贪心最大匹配算法按顺序查找已知最长子词。</p>
     *
     * @param text 要分词的输入字符串。
     * @return 字符串标记的 {@link List}。
     */
    public List<String> tokenize(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();
        int length = text.length();
        int index = 0;

        // 统计词表中最长词的长度，避免无谓的长文本截取与查找尝试
        int maxVocabLen = 0;
        for (String v : vocab) {
            maxVocabLen = Math.max(maxVocabLen, v.length());
        }
        if (maxVocabLen == 0) {
            maxVocabLen = 1;
        }

        // 贪心最长匹配：直接在原 String 上通过指针偏移扫描，彻底消除了 List<String> 频繁分词与 String.join 拼接带来的海量 GC 内存抖动
        while (index < length) {
            String longest = "";
            int maxLimit = Math.min(maxVocabLen, length - index);

            // 从可能匹配的最长词长度开始递减搜索
            for (int len = maxLimit; len > 0; len--) {
                String candidate = text.substring(index, index + len);
                if (vocab.contains(candidate)) {
                    longest = candidate;
                    // 从最大长度向下遍历，第一个命中的就是最长匹配，可以直接 break 退出，避开 O(N) 冗余匹配
                    break;
                }
            }

            if (!longest.isEmpty()) {
                result.add(longest);
                index += longest.length();
            } else {
                // 分词器遇到了“读不懂的陌生字符”。它的业务价值是：“虽然我不认识这个字符，但我把它单独切出来，并保证程序能继续往后读，绝不卡死。”
                result.add(String.valueOf(text.charAt(index)));
                index++;
            }
        }
        return result;
    }

    /**
     * <p>获取训练好的词表集合。</p>
     *
     * @return 不可修改的词表子词 {@link Set}。
     */
    public Set<String> getVocab() {
        return Collections.unmodifiableSet(vocab);
    }


}