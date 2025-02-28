package cn.net.pap.common.datastructure.tokenization;

import java.util.*;
import java.util.stream.Collectors;

public class BPETokenization {

    // 词表
    private Set<String> vocab = new HashSet<>();

    // 训练过程的序列
    private List<List<String>> sequences = new ArrayList<>();

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

    public Set<String> getVocab() {
        return Collections.unmodifiableSet(vocab);
    }


}
