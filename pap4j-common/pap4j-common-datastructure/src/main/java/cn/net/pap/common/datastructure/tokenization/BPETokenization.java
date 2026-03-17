package cn.net.pap.common.datastructure.tokenization;

import java.util.*;
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
        // 转换为字符列表
        List<String> chars = text.chars().mapToObj(c -> String.valueOf((char) c)).collect(Collectors.toList());

        // 贪心最大匹配
        List<String> result = new ArrayList<>();
        while (!chars.isEmpty()) {
            String longest = "";
            for (int len = chars.size(); len > 0; len--) {
                String candidate = String.join("", chars.subList(0, len));
                if (vocab.contains(candidate) && candidate.length() > longest.length()) {
                    longest = candidate;
                }
            }
            if (!longest.isEmpty()) {
                result.add(longest);
                chars = chars.subList(longest.length(), chars.size());
            } else {
                result.add(chars.get(0));
                chars = chars.subList(1, chars.size());
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