package cn.net.pap.common.datastructure.state;

import java.util.ArrayList;
import java.util.List;

/**
 * <p><strong>StatusFlowStateMachine</strong> 定义了一个用于跟踪离散状态流的静态转换矩阵。</p>
 *
 * <p>它使用二维数组对编号事件之间的有向边进行建模，并提供查询路径和关系的方法。</p>
 *
 * <ul>
 *     <li>查找从根节点到叶节点的所有路径。</li>
 *     <li>枚举所有有效的转换路径。</li>
 *     <li>查询前置和后继的链接事件。</li>
 * </ul>
 */
public class StatusFlowStateMachine {

    /**
     * <p>表示状态机的命名事件的字符串常量。</p>
     */
    private static final String[] eventTable = {
            "事件1",
            "事件2",
            "事件3",
            "事件4",
            "事件5",
            "事件6",
            "事件7",
            "事件8",
            "事件9",
            "事件10"
    };

    /**
     * <p>定义有效状态转换的 N*N 邻接矩阵。</p>
     * 
     * <ul>
     *   <li><strong>-1</strong>: 自环（无操作）。</li>
     *   <li><strong>1</strong>: 到目标状态的有效转换边。</li>
     *   <li><strong>0</strong>: 无法直接转换。</li>
     * </ul>
     */
    private static final Integer[][] transitionTable = {
            {-1, 1, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, -1, 1, 1, 1, 1, 0, 1, 0, 0},
            {0, 0, -1, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, -1, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, -1, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, -1, 1, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, -1, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, -1, 1, 1},
            {0, 0, 0, 0, 0, 0, 0, 0, -1, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0, -1},
    };

    /**
     * <p>从初始根节点遍历状态机图，并收集在叶节点终止的所有路径。</p>
     *
     * @return 包含完整路径的 {@link List}，其中每条路径是事件名称的列表。
     */
    public static List<List<String>> getPathsFromRootToLeaf() {
        List<List<String>> rootToLeafPaths = new ArrayList<>();
        boolean[] visited = new boolean[transitionTable.length];
        List<String> path = new ArrayList<>();

        dfsFromRootToLeaf(transitionTable, 0, visited, path, rootToLeafPaths);

        return rootToLeafPaths;
    }

    /**
     * <p>递归深度优先搜索以定位叶节点路径。</p>
     *
     * @param matrix          邻接矩阵。
     * @param current         当前节点索引。
     * @param visited         访问跟踪数组。
     * @param path            当前活跃路径。
     * @param rootToLeafPaths 完整路径的累加器列表。
     */
    private static void dfsFromRootToLeaf(Integer[][] matrix, int current, boolean[] visited, List<String> path, List<List<String>> rootToLeafPaths) {
        visited[current] = true;
        path.add(eventTable[current]);

        boolean isLeaf = true;
        for (int i = 0; i < matrix.length; i++) {
            if (matrix[current][i] == 1 && !visited[i]) {
                isLeaf = false;
                dfsFromRootToLeaf(matrix, i, visited, path, rootToLeafPaths);
            }
        }

        // 如果当前节点是叶子节点，则记录路径
        if (isLeaf) {
            rootToLeafPaths.add(new ArrayList<>(path));
        }

        path.remove(path.size() - 1);
        visited[current] = false;
    }

    /**
     * <p>收集从任何节点开始的整个图中所有可能的转换路径。</p>
     *
     * @return 包含所有路径的 {@link List}。
     */
    public static List<List<String>> getAllPath() {
        List<List<String>> allPaths = new ArrayList<>();
        boolean[] visited = new boolean[transitionTable.length];

        for (int i = 0; i < transitionTable.length; i++) {
            List<String> singleNodePath = new ArrayList<>();
            singleNodePath.add(eventTable[i]);
            allPaths.add(singleNodePath);
        }

        for (int i = 0; i < transitionTable.length; i++) {
            dfs(transitionTable, i, visited, new ArrayList<>(), allPaths);
        }

        return allPaths;
    }

    /**
     * <p>递归深度优先搜索以枚举所有连接的边。</p>
     *
     * @param matrix   转换矩阵。
     * @param current  当前状态节点索引。
     * @param visited  访问跟踪数组。
     * @param path     活跃的累积路径。
     * @param allPaths 保存所有可能序列的累加器列表。
     */
    private static void dfs(Integer[][] matrix, int current, boolean[] visited, List<String> path, List<List<String>> allPaths) {
        visited[current] = true;
        path.add(eventTable[current]);

        boolean hasNext = false;
        for (int i = 0; i < matrix.length; i++) {
            if (matrix[current][i] == 1 && !visited[i]) {
                hasNext = true;
                dfs(matrix, i, visited, path, allPaths);
            }
        }

        if (path.size() > 1) {
            allPaths.add(new ArrayList<>(path));
        }

        path.remove(path.size() - 1);
        visited[current] = false;
    }

    /**
     * <p>查询指定事件的所有可能的直接后继事件。</p>
     *
     * @param eventName 源事件的确切名称。
     * @return 目标事件名称的 {@link List}。
     */
    public static List<String> getNextEventByName(String eventName) {
        List<String> returnList = new ArrayList<>();

        Integer eventLevel = -1;
        for (Integer eventTableIdx = 0; eventTableIdx < eventTable.length; eventTableIdx++) {
            if (eventTable[eventTableIdx].equals(eventName)) {
                eventLevel = eventTableIdx;
                break;
            }
        }
        if (eventLevel != -1) {
            Integer[] rowEvent = transitionTable[eventLevel];
            for (Integer rowEventIdx = 0; rowEventIdx < rowEvent.length; rowEventIdx++) {
                if (rowEvent[rowEventIdx] == 1) {
                    returnList.add(eventTable[rowEventIdx]);
                }
            }
        }

        return returnList;
    }

    /**
     * <p>查询指向指定事件的所有直接前置事件。</p>
     *
     * @param eventName 目标事件的确切名称。
     * @return 源事件名称的 {@link List}。
     */
    public static List<String> getBeforeEventByName(String eventName) {
        List<String> returnList = new ArrayList<>();
        List<Integer> returnIdxList = new ArrayList<>();

        Integer eventLevel = -1;
        for (Integer eventTableIdx = 0; eventTableIdx < eventTable.length; eventTableIdx++) {
            if (eventTable[eventTableIdx].equals(eventName)) {
                eventLevel = eventTableIdx;
                break;
            }
        }

        for (Integer outIdx = 0; outIdx < transitionTable.length; outIdx++) {
            if(transitionTable[outIdx][eventLevel] == 1) {
                returnIdxList.add(outIdx);
            }
        }

        for (Integer idx = 0; idx < returnIdxList.size(); idx++) {
            returnList.add(eventTable[returnIdxList.get(idx)]);
        }

        return returnList;
    }

}