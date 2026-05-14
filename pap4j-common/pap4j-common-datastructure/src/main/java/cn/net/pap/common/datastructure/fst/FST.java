package cn.net.pap.common.datastructure.fst;

import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <h1>有限状态机 / 字典树状态节点 (FST - Finite State Transducer/Trie)</h1>
 * <p>表示一个支持高并发和扩展区字符（Emoji）的动态前缀树/状态机数据结构。</p>
 * <p>常用于高性能的词库检索、敏感词过滤或前缀匹配等场景。</p>
 * <ul>
 *     <li>追加词条: {@link #addWord(String)}</li>
 *     <li>检索词条: {@link #isWord(String)}</li>
 *     <li>移除词条: {@link #removeWord(String)}</li>
 * </ul>
 *
 * @author alexgaoyh
 */
public class FST implements Serializable {

    /**
     * <p>管理当前节点所有往后状态转移的字典映射。</p>
     * <p>使用了 {@link ConcurrentHashMap} 以保证多线程环境下的线程安全性。</p>
     */
    private ConcurrentHashMap<String, FST> transitions = new ConcurrentHashMap<>(10000);

    /**
     * <p>标记当前节点是否可以被认为是一个词条的合法结尾点。</p>
     */
    private boolean isFinalState = false;

    /**
     * <p>向当前 FST 状态机中逐层添加指定的词语。</p>
     * <p>方法会在向下递归时自动处理由代码点代理对组合成的占多字符的复杂符号。</p>
     *
     * @param word 想要添加到字典树中的目标词汇
     */
    public void addWord(String word) {
        if (word == null || word.isEmpty()) {
            isFinalState = true;
            return;
        }
        addWord(word.codePoints().toArray(), 0);
    }

    private void addWord(int[] codePoints, int index) {
        if (index == codePoints.length) {
            isFinalState = true;
            return;
        }
        String cStr = new String(Character.toChars(codePoints[index]));
        FST nextState = transitions.computeIfAbsent(cStr, k -> new FST());
        nextState.addWord(codePoints, index + 1);
    }

    /**
     * <p>检索并判定所给出的词汇是否完整存在于当前状态机中。</p>
     *
     * @param word 需要检查匹配情况的待查词条
     * @return 如果词汇在字典中并且被标识为一个完整词结尾则返回 {@code true}，否则返回 {@code false}
     */
    public boolean isWord(String word) {
        if (word == null || word.isEmpty()) {
            return isFinalState;
        }
        return isWord(word.codePoints().toArray(), 0);
    }

    private boolean isWord(int[] codePoints, int index) {
        if (index == codePoints.length) {
            return isFinalState;
        }
        String cStr = new String(Character.toChars(codePoints[index]));
        FST nextState = transitions.get(cStr);
        if (nextState == null) {
            return false;
        }
        return nextState.isWord(codePoints, index + 1);
    }

    /**
     * <p>尝试从字典中移除指定的词语，并同时向上传递清理由于移除导致的多余孤立状态节点。</p>
     *
     * @param word 需要被删除的具体词条文本
     * @return 如果这个词条被成功找到并移除了则返回 {@code true}，否则（不存在此词）返回 {@code false}
     */
    public boolean removeWord(String word) {
        if (word == null || word.isEmpty()) {
            boolean wasFinal = isFinalState;
            isFinalState = false;
            return wasFinal;
        }
        return removeWord(word.codePoints().toArray(), 0);
    }

    private boolean removeWord(int[] codePoints, int index) {
        if (index == codePoints.length) {
            boolean wasFinal = isFinalState;
            isFinalState = false;
            return wasFinal;
        }
        String cStr = new String(Character.toChars(codePoints[index]));
        FST nextState = transitions.get(cStr);
        if (nextState == null) {
            return false;
        }
        boolean wasRemoved = nextState.removeWord(codePoints, index + 1);
        
        // 当下一个状态节点移除词后既不是独立词的结尾，也没有向下的子节点转移边时，可以直接安全剪枝卸除
        if (nextState.transitions.isEmpty() && !nextState.isFinalState) {
            transitions.remove(cStr);
        }
        return wasRemoved;
    }

}
