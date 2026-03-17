package cn.net.pap.common.datastructure.selector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * <p><strong>IntervalSelector</strong> 提供了冲突检测和区间选择算法。</p>
 *
 * <p>它实现了标准的贪心算法，用于选择非重叠区间并基于包含关系过滤区间。</p>
 *
 * <ul>
 *     <li>贪心选择最大非冲突区间。</li>
 *     <li>检测完全被外部区间包含的内部区间。</li>
 * </ul>
 */
public class IntervalSelector {

    /**
     * <p><strong>Interval</strong> 表示具有开始和结束边界的离散数学范围。</p>
     */
    public static class Interval {
        /** <p>区间的起点。</p> */
        public int start;
        /** <p>区间的终点。</p> */
        public int end;
        /** <p>包含在其中的详细子区间列表。</p> */
        public List<Interval> details = new ArrayList<>();

        /**
         * <p>构造一个具有给定边界的区间。</p>
         *
         * @param start 开始边界。
         * @param end   结束边界。
         */
        public Interval(int start, int end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public String toString() {
            return "[" + start + "," + end +  "," + details + "]";
        }
    }

    /**
     * <p>基于最早结束时间使用贪心算法选择相互不冲突的区间的最大子集。</p>
     *
     * @param intervals 候选区间列表。
     * @return 非冲突区间的 {@link List}。
     */
    public static List<Interval> selectNonConflictingIntervals(List<Interval> intervals) {
        List<Interval> result = new ArrayList<>();
        if (intervals == null || intervals.isEmpty()) {
            return result;
        }

        // 1 按结束时间升序排序
        Collections.sort(intervals, new Comparator<Interval>() {
            @Override
            public int compare(Interval a, Interval b) {
                return Integer.compare(a.end, b.end);
            }
        });

        // 2 贪心选择区间
        int lastEnd = Integer.MIN_VALUE;
        for (Interval cur : intervals) {
            if (cur.start >= lastEnd) {
                result.add(cur);       // 保留当前区间
                lastEnd = cur.end;     // 更新结束时间
            }
            // 否则忽略冲突区间
        }

        return result;
    }

    /**
     * <p>过滤并选择完全包含在至少一个外部区间内的内部区间。</p>
     *
     * @param outer 外部边界区间集合。
     * @param inner 内部候选区间集合。
     * @return 完全被包围的内部区间的 {@link List}。
     */
    public static List<Interval> selectContainedIntervals(List<Interval> outer, List<Interval> inner) {
        List<Interval> result = new ArrayList<>();
        if (outer == null || inner == null || outer.isEmpty() || inner.isEmpty()) {
            return result;
        }

        List<Interval> outerCopy = new ArrayList<>(outer);
        List<Interval> innerCopy = new ArrayList<>(inner);

        outerCopy.sort(Comparator.comparingInt(i -> i.start));
        innerCopy.sort(Comparator.comparingInt(i -> i.start));

        for (Interval in : innerCopy) {
            boolean contained = false;
            for (Interval out : outerCopy) {
                if (in.start >= out.start && in.end <= out.end) {
                    contained = true;
                    break;
                }
            }
            if (contained) {
                result.add(in);
            }
        }
        return result;
    }

    /**
     * <p>将内部区间分组到包含它们的外部区间中。</p>
     *
     * <p>此方法通过将匹配的内部区间添加到提供的外部区间对象的 <code>details</code> 列表中来修改这些对象。</p>
     *
     * @param outer 外部边界区间集合。
     * @param inner 内部候选区间集合。
     * @return 更新后的外部区间列表。
     */
    public static List<Interval> selectContainedIntervals2(List<Interval> outer, List<Interval> inner) {
        List<Interval> outerCopy = new ArrayList<>(outer);
        List<Interval> innerCopy = new ArrayList<>(inner);

        outerCopy.sort(Comparator.comparingInt(i -> i.start));
        innerCopy.sort(Comparator.comparingInt(i -> i.start));

        // 遍历每个 inner 区间，找到它属于哪个 outer 区间
        for (Interval in : innerCopy) {
            for (Interval out : outerCopy) {
                if (in.start >= out.start && in.end <= out.end) {
                    out.details.add(in);
                    break;
                }
            }
        }

        return outerCopy;
    }
}