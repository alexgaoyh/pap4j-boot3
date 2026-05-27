package cn.net.pap.common.qlexpress.operator;

import com.alibaba.qlexpress4.runtime.Parameters;
import com.alibaba.qlexpress4.runtime.QContext;
import com.alibaba.qlexpress4.runtime.function.CustomFunction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * <p>叶子节点路径提取算子。</p>
 * <p>该算子仅提取从根节点到叶子节点（末端节点）的路径，忽略所有中间节点的路径。</p>
 * <p>例如：A -> B -> C，结果仅包含 [A > B > C]</p>
 * <p>用法：TREE_LEAF_FLATTEN(root, 'childrenKey', 'valueKey', 'separator')</p>
 */
public class TreeLeafFlattenOperator implements CustomFunction {

    @Override
    public Object call(QContext qContext, Parameters parameters) throws Throwable {
        if (parameters.size() < 3) {
            throw new IllegalArgumentException("TREE_LEAF_FLATTEN 需要至少3个参数：根对象, 子节点Key, 属性Key, [路径分隔符]");
        }

        Object root = parameters.get(0).get();
        Object childrenKeyObj = parameters.get(1).get();
        Object valueKeyObj = parameters.get(2).get();
        Object sepObj = parameters.size() > 3 ? parameters.get(3).get() : " > ";

        if (root == null || childrenKeyObj == null || valueKeyObj == null) {
            return new ArrayList<>();
        }

        String childrenKey = childrenKeyObj.toString();
        String valueKey = valueKeyObj.toString();
        String sep = sepObj == null ? " > " : sepObj.toString();

        List<String> result = new ArrayList<>();
        flatten(root, childrenKey, valueKey, sep, "", result);
        return result;
    }

    private void flatten(Object node, String childrenKey, String valueKey, String sep, String parentPath, List<String> result) {
        if (node == null) {
            return;
        }

        // 处理集合（如 children 数组）
        if (node instanceof Collection<?> coll) {
            for (Object item : coll) {
                flatten(item, childrenKey, valueKey, sep, parentPath, result);
            }
            return;
        }

        // 处理 Map 对象
        if (node instanceof Map<?, ?> map) {
            Object val = map.get(valueKey);
            String nodeValue = (val == null) ? "" : val.toString();

            // 构建当前路径
            String currentPath = parentPath.isEmpty() ? nodeValue : parentPath + sep + nodeValue;

            // 递归子节点
            Object children = map.get(childrenKey);
            if (children == null || (children instanceof Collection<?> coll && coll.isEmpty())) {
                // 如果没有子节点或子节点为空列表，则判定为叶子节点
                result.add(currentPath);
            } else {
                // 否则继续递归
                flatten(children, childrenKey, valueKey, sep, currentPath, result);
            }
        }
    }
}
