package cn.net.pap.common.datastructure.fstunicode.fastutil;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.io.Serializable;

public class FSTUnicode implements Serializable {

    private Int2ObjectMap<FSTUnicode> transitions;
    private boolean isFinalState = false;

    private static final float LOAD_FACTOR = 0.5f; // 可根据实际调整

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
            transitions = new Int2ObjectOpenHashMap<>(4, LOAD_FACTOR);
        }

        FSTUnicode next = transitions.get(codePoint);
        if (next == null) {
            next = new FSTUnicode();
            transitions.put(codePoint, next);
        }
        next.addWord(chars, index + charCount, length);
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

        boolean removed = next.removeWord(word, index + Character.charCount(codePoint));

        if (removed && (next.transitions == null || next.transitions.isEmpty()) && !next.isFinalState) {
            transitions.remove(codePoint);
            if (transitions.isEmpty()) {
                transitions = null;
            }
        }
        return removed;
    }

    // ----------------- 压缩 transitions -----------------
    public void rehash() {
        if (transitions != null && !transitions.isEmpty()) {
            ((Int2ObjectOpenHashMap<FSTUnicode>) transitions).trim();
            for (FSTUnicode child : transitions.values()) {
                if (child != null) child.rehash();
            }
        }
    }
}


