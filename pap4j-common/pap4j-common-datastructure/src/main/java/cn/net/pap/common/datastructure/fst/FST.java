package cn.net.pap.common.datastructure.fst;

import java.io.Serializable;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FST
 */
public class FST implements Serializable {

    private ConcurrentHashMap<String, FST> transitions = new ConcurrentHashMap<>(10000);
    private boolean isFinalState = false;

    public void addWord(String word) {
        if (word.isEmpty()) {
            isFinalState = true;
            return;
        }
        char c = word.charAt(0);
        String cStr = "";
        Integer length = 1;
        if (Character.isHighSurrogate(c)) {
            cStr = new String(Character.toChars(Character.toCodePoint(c, word.charAt(1))));
            length = 2;
        } else {
            cStr = c + "";
        }
        FST nextState = transitions.get(cStr);
        if (nextState == null) {
            nextState = new FST();
            transitions.put(cStr, nextState);
        }
        nextState.addWord(word.substring(length));
    }

    public boolean isWord(String word) {
        if (word.isEmpty()) {
            return isFinalState;
        }
        char c = word.charAt(0);
        String cStr = "";
        Integer length = 1;
        if (Character.isHighSurrogate(c)) {
            cStr = new String(Character.toChars(Character.toCodePoint(c, word.charAt(1))));
            length = 2;
        } else {
            cStr = c + "";
        }
        FST nextState = transitions.get(cStr);
        if (nextState == null) {
            return false;
        }
        return nextState.isWord(word.substring(length));
    }

    public boolean removeWord(String word) {
        if (word.isEmpty()) {
            boolean wasFinal = isFinalState;
            isFinalState = false;
            return wasFinal;
        }
        char c = word.charAt(0);
        String cStr = "";
        Integer length = 1;
        if (Character.isHighSurrogate(c)) {
            cStr = new String(Character.toChars(Character.toCodePoint(c, word.charAt(1))));
            length = 2;
        } else {
            cStr = c + "";
        }
        FST nextState = transitions.get(cStr);
        if (nextState == null) {
            return false;
        }
        boolean wasRemoved = nextState.removeWord(word.substring(length));
        if (nextState.transitions.isEmpty() && !nextState.isFinalState) {
            transitions.remove(cStr);
        }
        return wasRemoved;
    }

}
