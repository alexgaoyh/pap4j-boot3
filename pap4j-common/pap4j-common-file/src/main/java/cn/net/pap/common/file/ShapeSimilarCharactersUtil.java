package cn.net.pap.common.file;

import cn.net.pap.common.file.trie.TrieNode;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 形近字的工具类
 */
public class ShapeSimilarCharactersUtil {

    private TrieNode root;

    public ShapeSimilarCharactersUtil() {
        root = new TrieNode();
    }

    // 加载文件
    public void loadFile(String filename) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                insertLine(line);
            }
        }
    }

    // 插入一行形近字
    private void insertLine(String line) {
        Set<Character> chars = new HashSet<>();
        for (char c : line.toCharArray()) {
            chars.add(c);
        }
        for (char c : line.toCharArray()) {
            root.insert(c, chars);
        }
    }

    // 查询
    public Set<Character> querySimilarCharacters(char c) {
        TrieNode node = root;
        if (node.getChildren().containsKey(c)) {
            return node.getChildren().get(c).getSimilarChars();
        }
        return Collections.emptySet();
    }

}
