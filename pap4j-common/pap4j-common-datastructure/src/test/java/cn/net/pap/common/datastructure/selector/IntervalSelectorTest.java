package cn.net.pap.common.datastructure.selector;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class IntervalSelectorTest {

    /**
     * 最早结束时间优先贪心算法 保留尽可能多的不冲突区间
     */
    @Test
    public void selectNonConflictingIntervalsTest() {
        List<IntervalSelector.Interval> intervals = new ArrayList<>();
        intervals.add(new IntervalSelector.Interval(1, 3));
        intervals.add(new IntervalSelector.Interval(2, 4));
        intervals.add(new IntervalSelector.Interval(3, 5));
        intervals.add(new IntervalSelector.Interval(0, 6));
        intervals.add(new IntervalSelector.Interval(5, 7));
        intervals.add(new IntervalSelector.Interval(8, 9));

        List<IntervalSelector.Interval> selected = IntervalSelector.selectNonConflictingIntervals(intervals);
        System.out.println("Selected non-conflicting intervals:");
        for (IntervalSelector.Interval i : selected) {
            System.out.println(i);
        }
    }

    @Test
    public void selectContainedIntervalsTest() {
        List<IntervalSelector.Interval> outer = List.of(
                new IntervalSelector.Interval(0, 10),
                new IntervalSelector.Interval(20, 30)
        );

        List<IntervalSelector.Interval> inner = List.of(
                new IntervalSelector.Interval(1, 5),
                new IntervalSelector.Interval(9, 11),
                new IntervalSelector.Interval(25, 27),
                new IntervalSelector.Interval(31, 32)
        );

        List<IntervalSelector.Interval> result = IntervalSelector.selectContainedIntervals(outer, inner);
        System.out.println(result);  // 输出 [[1,5], [25,27]]

        List<IntervalSelector.Interval> result2 = IntervalSelector.selectContainedIntervals2(outer, inner);
        System.out.println(result2);

    }
}
