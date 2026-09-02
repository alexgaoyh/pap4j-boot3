package cn.net.pap.common.qlexpress.operator;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>列表类算子（LIST_SUM / LIST_AVG / LIST_MAX / LIST_MIN）内部共享的工具方法。</p>
 * <p>包内私有实现细节，不构成公共 API。</p>
 */
final class ListSupport {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ListSupport.class);

    private ListSupport() {
    }

    /**
     * 将输入归一化为可迭代集合：支持 Collection / 数组 / Iterable，其余类型抛错。
     */
    static Iterable<?> iterableOf(Object input) {
        if (input instanceof Iterable<?> iterable) {
            return iterable;
        }
        if (input.getClass().isArray()) {
            int length = Array.getLength(input);
            List<Object> elements = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                elements.add(Array.get(input, i));
            }
            return elements;
        }
        throw new IllegalArgumentException(
                "List operator requires a collection or array, but got: " + input.getClass().getName());
    }

    /**
     * 元素转 BigDecimal：null 返回 null（调用方跳过），Number / 数字字符串强转，其余类型抛错。
     */
    static BigDecimal toBigDecimal(Object element) {
        if (element == null) {
            return null;
        }
        if (element instanceof BigDecimal decimal) {
            return decimal;
        }
        if (element instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (element instanceof String str) {
            try {
                return new BigDecimal(str.trim());
            } catch (NumberFormatException e) {
                log.error("Invalid numeric element: {}", str, e);
                throw new IllegalArgumentException("Expected a numeric element, but got: " + str, e);
            }
        }
        throw new IllegalArgumentException("Expected a numeric element, but got: " + element);
    }

    /**
     * 通用 Comparable 比较，类型不可比较时抛出含双方类型的明确错误。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static int compare(Object a, Object b) {
        if (!(a instanceof Comparable)) {
            throw new IllegalArgumentException(
                    "LIST_MAX/LIST_MIN requires Comparable elements, but got: " + a.getClass().getName());
        }
        try {
            return ((Comparable) a).compareTo(b);
        } catch (ClassCastException e) {
            log.error("Cannot compare elements of different types: {} vs {}",
                    a.getClass().getName(), b.getClass().getName(), e);
            throw new IllegalArgumentException(
                    "LIST_MAX/LIST_MIN cannot compare elements of different types: "
                            + a.getClass().getName() + " vs " + b.getClass().getName(), e);
        }
    }
}
