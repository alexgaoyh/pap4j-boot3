package cn.net.pap.common.datastructure.fst;

import java.io.Serializable;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FST
 */
public class FST implements Serializable {

    private ConcurrentHashMap<Character, FST> transitions = new ConcurrentHashMap<>(10000);
    private boolean isFinalState = false;

    public void addWord(String word) {
        if (word.isEmpty()) {
            isFinalState = true;
            return;
        }
        char c = word.charAt(0);
        FST nextState = transitions.get(c);
        if (nextState == null) {
            nextState = new FST();
            transitions.put(c, nextState);
        }
        nextState.addWord(word.substring(1));
    }

    public boolean isWord(String word) {
        if (word.isEmpty()) {
            return isFinalState;
        }
        char c = word.charAt(0);
        FST nextState = transitions.get(c);
        if (nextState == null) {
            return false;
        }
        return nextState.isWord(word.substring(1));
    }

    public boolean removeWord(String word) {
        if (word.isEmpty()) {
            boolean wasFinal = isFinalState;
            isFinalState = false;
            return wasFinal;
        }
        char c = word.charAt(0);
        FST nextState = transitions.get(c);
        if (nextState == null) {
            return false;
        }
        boolean wasRemoved = nextState.removeWord(word.substring(1));
        if (nextState.transitions.isEmpty() && !nextState.isFinalState) {
            transitions.remove(c);
        }
        return wasRemoved;
    }

}
