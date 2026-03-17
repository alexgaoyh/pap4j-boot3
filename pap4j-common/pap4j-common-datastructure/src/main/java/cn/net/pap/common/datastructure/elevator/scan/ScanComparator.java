package cn.net.pap.common.datastructure.elevator.scan;

import java.util.Comparator;

/**
 * <h1>扫描调度任务比较器 (Scan Comparator)</h1>
 * <p>实现任务 {@link Task} 的对比逻辑，支持基于当前楼层位置和初始运行方向，
 * 给出遵循 SCAN 算法规律的请求执行顺序。</p>
 *
 * @author alexgaoyh
 */
public class ScanComparator implements Comparator<Task> {

    /**
     * <p>电梯比较时的基准所在位置。</p>
     */
    private final int initPos;

    /**
     * <p>电梯初始运行方向。</p>
     */
    private final Direct direct;

    /**
     * <p>构造基于初始位置和方向的扫描比较器。</p>
     *
     * @param initPos 当前基准位置
     * @param direct  当前运行方向 {@link Direct}
     */
    public ScanComparator(int initPos, Direct direct) {
        this.initPos = initPos;
        this.direct = direct;
    }

    /**
     * <p>对比两个任务优先级的入口方法。</p>
     *
     * @param o1 任务一
     * @param o2 任务二
     * @return 如果 {@code o1} 应在 {@code o2} 之前服务，返回负整数；之后服务则返回正整数。
     */
    @Override
    public int compare(Task o1, Task o2) {
        int i = ifInitDirectIsUp(o1, o2);
        if (direct == Direct.UP) {
            return i;
        } else {
            return -i;
        }
    }

    /**
     * <p>假定电梯初始向上运行，计算两个任务的相对优先级。</p>
     * <p>它会同时考虑乘客的请求楼层以及他们期望的前往方向，以符合顺路接送的原则。</p>
     *
     * @param o1 任务一
     * @param o2 任务二
     * @return 相对优先级比较结果
     */
    public int ifInitDirectIsUp(Task o1, Task o2) {
        int i = biggerOrSmaller(o1, o2);
        Direct d1 = o1.getDirect();
        Direct d2 = o2.getDirect();
        if (d1 == d2) {
            if (d1 == Direct.UP) {
                if (o1.from > initPos) {
                    return i;
                } else {
                    return 1;
                }
            } else {
                if (o1.from > initPos) {
                    return -i;
                } else {
                    return 1;
                }
            }
        } else {
            if (d1 == Direct.UP) {
                if (o1.from > initPos) {
                    return -1;
                } else {
                    return 1;
                }
            } else {
                if (o1.from > initPos) {
                    return 1;
                } else {
                    return -1;
                }
            }
        }
    }

    /**
     * <p>仅基于出发楼层的高低大小进行简单的比较。</p>
     *
     * @param o1 任务一
     * @param o2 任务二
     * @return {@code 1}、{@code -1} 或 {@code 0}
     */
    public int biggerOrSmaller(Task o1, Task o2) {
        if (o1.from > o2.from) {
            return 1;
        } else if (o1.from < o2.from) {
            return -1;
        } else {
            return 0;
        }
    }
}