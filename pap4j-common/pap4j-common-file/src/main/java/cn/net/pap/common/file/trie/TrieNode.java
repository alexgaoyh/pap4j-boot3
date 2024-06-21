package cn.net.pap.common.file.trie;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TrieNode {
    private Map<Character, TrieNode> children;
    private Set<Character> similarChars;

    public TrieNode() {
        this.children = new HashMap<>();
        this.similarChars = new HashSet<>();
    }

    public void insert(char c, Set<Character> similarChars) {
        TrieNode node = this;
        node.children.putIfAbsent(c, new TrieNode());
        node = node.children.get(c);
        node.similarChars.addAll(similarChars);
    }

    // 将字符串转换为字符集
    private Set<Character> toCharSet(String word) {
        Set<Character> charSet = new HashSet<>();
        for (char c : word.toCharArray()) {
            charSet.add(c);
        }
        return charSet;
    }

    public Map<Character, TrieNode> getChildren() {
        return children;
    }

    public Set<Character> getSimilarChars() {
        return similarChars;
    }
}
