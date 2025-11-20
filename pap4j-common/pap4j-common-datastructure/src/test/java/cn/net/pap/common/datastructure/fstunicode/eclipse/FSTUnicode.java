package cn.net.pap.common.datastructure.fstunicode.eclipse;

import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;

import java.io.Serializable;

/**
 * FST - 基于Unicode码点的优化实现（使用Eclipse Collections 13.0.0）
 */
public class FSTUnicode implements Serializable {

    // 使用Eclipse Collections的IntObjectMap替代HashMap
    private MutableIntObjectMap<FSTUnicode> transitions;
    private boolean isFinalState = false;

    // ----------------- 添加单词 -----------------
    public void addWord(String word) {
        addWord((CharSequence) word, 0);
    }

    public void addWord(CharSequence word) {
        addWord(word, 0);
    }

    private void addWord(CharSequence word, int index) {
        if (index >= word.length()) {
            isFinalState = true;
            return;
        }

        int codePoint = Character.codePointAt(word, index);
        int charCount = Character.charCount(codePoint);

        if (transitions == null) {
            transitions = new org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap<>();
        }

        FSTUnicode next = transitions.get(codePoint);
        if (next == null) {
            next = new FSTUnicode();
            transitions.put(codePoint, next);
        }
        next.addWord(word, index + charCount);
    }

    public void addWord(char[] chars, int length) {
        addWord(chars, 0, length);
    }

    private void addWord(char[] chars, int index, int length) {
        if (index >= length) {
            isFinalState = true;
            return;
        }

        int codePoint = Character.codePointAt(chars, index, length);
        int charCount = Character.charCount(codePoint);

        if (transitions == null) {
            transitions = new org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap<>();
        }

        FSTUnicode next = transitions.get(codePoint);
        if (next == null) {
            next = new FSTUnicode();
            transitions.put(codePoint, next);
        }
        next.addWord(chars, index + charCount, length);
    }

    // ----------------- 检查单词 -----------------
    public boolean isWord(String word) {
        return isWord((CharSequence) word, 0);
    }

    public boolean isWord(CharSequence word) {
        return isWord(word, 0);
    }

    private boolean isWord(CharSequence word, int index) {
        if (index >= word.length()) return isFinalState;

        int codePoint = Character.codePointAt(word, index);
        int charCount = Character.charCount(codePoint);

        if (transitions == null) return false;
        FSTUnicode next = transitions.get(codePoint);
        if (next == null) return false;

        return next.isWord(word, index + charCount);
    }

    // ----------------- 删除单词 -----------------
    public boolean removeWord(String word) {
        return removeWord((CharSequence) word, 0);
    }

    public boolean removeWord(CharSequence word) {
        return removeWord(word, 0);
    }

    private boolean removeWord(CharSequence word, int index) {
        if (index >= word.length()) {
            boolean wasFinal = isFinalState;
            isFinalState = false;
            return wasFinal;
        }

        int codePoint = Character.codePointAt(word, index);
        int charCount = Character.charCount(codePoint);

        if (transitions == null) return false;
        FSTUnicode next = transitions.get(codePoint);
        if (next == null) return false;

        boolean removed = next.removeWord(word, index + charCount);

        // 清理空节点
        if (removed && (next.transitions == null || next.transitions.isEmpty()) && !next.isFinalState) {
            transitions.remove(codePoint);
            // 如果当前节点也变成空节点，清理transitions引用
            if (transitions.isEmpty()) {
                transitions = null;
            }
        }
        return removed;
    }

    // ----------------- 批量添加 -----------------
    public void addWords(String[] words) {
        for (String word : words) {
            addWord(word);
        }
    }

    // ----------------- 新增工具方法 -----------------

    /**
     * 获取当前节点的子节点数量
     */
    public int getTransitionCount() {
        return transitions == null ? 0 : transitions.size();
    }

    /**
     * 清空所有子节点
     */
    public void clearTransitions() {
        if (transitions != null) {
            transitions.clear();
            transitions = null;
        }
    }

    /**
     * 对当前节点的 transitions 进行紧凑化
     * 重新分配内部数组，消除删除操作产生的空洞
     */
    public void rehash() {
        if (transitions != null && !transitions.isEmpty()) {
            // 强转到 IntObjectHashMap 调用 compact
            org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap<FSTUnicode> map =
                    (org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap<FSTUnicode>) transitions;
            map.compact();
            // 递归对子节点也做相同处理
            for (FSTUnicode child : transitions.values()) {
                if (child != null) {
                    child.rehash();
                }
            }
        }
    }


}