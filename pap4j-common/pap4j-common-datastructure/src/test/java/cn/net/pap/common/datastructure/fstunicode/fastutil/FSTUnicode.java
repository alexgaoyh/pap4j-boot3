package cn.net.pap.common.datastructure.fstunicode.fastutil;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.io.Serializable;

public class FSTUnicode implements Serializable {

    private Int2ObjectMap<FSTUnicode> transitions;
    private boolean isFinalState = false;

    // 共享的空叶子节点 - 只读终止状态
    private static final FSTUnicode EMPTY_LEAF = new FSTUnicode(true);

    private static final float LOAD_FACTOR = 0.5f;

    // 默认构造函数
    public FSTUnicode() {
    }

    // 私有构造函数，用于创建共享的只读叶子节点
    private FSTUnicode(boolean isFinal) {
        this.isFinalState = isFinal;
        // transitions 保持为 null，表示叶子节点
    }

    // ----------------- 添加单词 -----------------
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
            transitions = new Int2ObjectOpenHashMap<>(4, LOAD_FACTOR);
        }

        FSTUnicode next = transitions.get(codePoint);
        if (next == null) {
            // 如果是最后一个字符，使用共享的叶子节点
            if (index + charCount >= word.length()) {
                next = EMPTY_LEAF;
            } else {
                next = new FSTUnicode();
            }
            transitions.put(codePoint, next);
        } else if (next == EMPTY_LEAF) {
            // 如果当前是共享叶子节点，需要替换为可写的节点
            next = new FSTUnicode();
            next.isFinalState = true; // 保持终止状态
            transitions.put(codePoint, next);
        }

        if (next != EMPTY_LEAF) {
            next.addWord(word, index + charCount);
        }
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
            transitions = new Int2ObjectOpenHashMap<>(4, LOAD_FACTOR);
        }

        FSTUnicode next = transitions.get(codePoint);
        if (next == null) {
            // 如果是最后一个字符，使用共享的叶子节点
            if (index + charCount >= length) {
                next = EMPTY_LEAF;
            } else {
                next = new FSTUnicode();
            }
            transitions.put(codePoint, next);
        } else if (next == EMPTY_LEAF) {
            // 如果当前是共享叶子节点，需要替换为可写的节点
            next = new FSTUnicode();
            next.isFinalState = true; // 保持终止状态
            transitions.put(codePoint, next);
        }

        if (next != EMPTY_LEAF) {
            next.addWord(chars, index + charCount, length);
        }
    }

    // ----------------- 检查单词 -----------------
    public boolean isWord(CharSequence word) {
        return isWord(word, 0);
    }

    private boolean isWord(CharSequence word, int index) {
        if (index >= word.length()) return isFinalState;

        if (transitions == null) return false;

        int codePoint = Character.codePointAt(word, index);
        FSTUnicode next = transitions.get(codePoint);
        if (next == null) return false;

        // 对于共享叶子节点，检查是否到达字符串末尾
        if (next == EMPTY_LEAF) {
            return index + Character.charCount(codePoint) >= word.length();
        }

        return next.isWord(word, index + Character.charCount(codePoint));
    }

    // ----------------- 删除单词 -----------------
    public boolean removeWord(CharSequence word) {
        return removeWord(word, 0);
    }

    private boolean removeWord(CharSequence word, int index) {
        if (index >= word.length()) {
            boolean wasFinal = isFinalState;
            isFinalState = false;
            return wasFinal;
        }

        if (transitions == null) return false;

        int codePoint = Character.codePointAt(word, index);
        FSTUnicode next = transitions.get(codePoint);
        if (next == null) return false;

        // 如果是共享叶子节点，直接移除并返回true
        if (next == EMPTY_LEAF) {
            transitions.remove(codePoint);
            if (transitions.isEmpty()) {
                transitions = null;
            }
            return true;
        }

        boolean removed = next.removeWord(word, index + Character.charCount(codePoint));

        if (removed) {
            // 检查子节点是否可以清理
            if (next.transitions == null || next.transitions.isEmpty()) {
                if (!next.isFinalState) {
                    // 如果不是终止状态，直接移除
                    transitions.remove(codePoint);
                    if (transitions.isEmpty()) {
                        transitions = null;
                    }
                } else if (next.transitions == null || next.transitions.isEmpty()) {
                    // 如果是终止状态但没有子节点，替换为共享叶子节点
                    transitions.put(codePoint, EMPTY_LEAF);
                }
            }
        }
        return removed;
    }

    // ----------------- 压缩 transitions -----------------
    public void rehash() {
        if (transitions != null && !transitions.isEmpty()) {
            ((Int2ObjectOpenHashMap<FSTUnicode>) transitions).trim();
            for (FSTUnicode child : transitions.values()) {
                if (child != null && child != EMPTY_LEAF) {
                    child.rehash();
                }
            }
        }
    }

    // 辅助方法：检查节点是否可以替换为共享叶子节点
    private boolean canBeReplacedWithEmptyLeaf() {
        return isFinalState && (transitions == null || transitions.isEmpty());
    }
}