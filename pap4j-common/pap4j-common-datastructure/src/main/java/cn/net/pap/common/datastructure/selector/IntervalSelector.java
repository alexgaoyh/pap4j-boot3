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

        public Interval(int start, int end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public String toString() {
            return "[" + start + "," + end + "]";
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

}

