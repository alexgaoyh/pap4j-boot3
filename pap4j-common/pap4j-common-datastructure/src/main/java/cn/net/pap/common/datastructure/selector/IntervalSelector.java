package cn.net.pap.common.datastructure.selector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 冲突检测算法（Conflict Detection Algorithm）
 * <p>
 * 区间冲突选择 / 过滤
 */
public class IntervalSelector {

    // 区间类
    public static class Interval {
        public int start;
        public int end;
        public List<Interval> details = new ArrayList<>();

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
     * 最早结束时间优先贪心算法 保留尽可能多的不冲突区间
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
     * 过滤算法：inner 区间必须完全位于 outer 区间集合的任意一个区间内
     *
     * @param outer 外层区间集合
     * @param inner 内层区间集合
     * @return 所有被 outer 区间完全包裹的 inner 区间
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

