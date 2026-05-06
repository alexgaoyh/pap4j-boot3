package cn.net.pap.common.datastructure.elevator.scan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * <h1>扫描算法调度器 (SCAN / Elevator Scheduling)</h1>
 * <p>模拟电梯调度中的 SCAN 算法（又称电梯算法）。</p>
 * <p>在该算法中，电梯保持当前方向运行直至没有请求，或到达极限位置后，才会改变方向处理反向的请求。</p>
 * 
 * @author alexgaoyh
 */
public class SCAN {

    private static final Logger log = LoggerFactory.getLogger(SCAN.class);

    /**
     * <p>电梯当前运行的初始方向。</p>
     */
    private Direct direct;

    /**
     * <p>支持的最小楼层（或磁道号）。</p>
     */
    private int min;

    /**
     * <p>支持的最大楼层（或磁道号）。</p>
     */
    private int max;

    /**
     * <p>待处理的乘客请求（任务）列表。</p>
     */
    private List<Task> taskList;

    /**
     * <p>电梯当前的初始位置。</p>
     */
    private int initPos;

    /**
     * <p>构造一个 SCAN 调度器实例。</p>
     *
     * @param tasks   初始的乘客请求列表
     * @param initPos 电梯当前的楼层位置
     * @param direct  电梯当前的运行方向 {@link Direct}
     * @param min     电梯可运行的最小楼层
     * @param max     电梯可运行的最大楼层
     */
    public SCAN(List<Task> tasks, int initPos, Direct direct, int min, int max) {
        this.taskList = new ArrayList<>(tasks);
        this.initPos = initPos;
        this.direct = direct;
        this.min = min;
        this.max = max;
    }

    /**
     * <p>根据 SCAN 算法对现有的任务列表重新排序（分配调度）。</p>
     * <p>会使用 {@link ScanComparator} 根据初始位置和方向得出正确的服务顺序。</p>
     */
    protected void dispatching() {
        List<Task> visit = new ArrayList<>(taskList);
        visit.sort(new ScanComparator(initPos, direct));
        taskList.clear();
        taskList.addAll(visit);
    }

    /**
     * <p>执行电梯调度模拟，并在控制台打印调度日志及统计信息。</p>
     * <p>统计信息包含所有任务的总移动距离以及平均每次服务的移动距离。</p>
     */
    public void exec() {
        log.info(String.format("电梯当前位于第%s层, 对如下乘客进行服务:", initPos));
        StringBuilder sb = new StringBuilder();
        for (Task task : taskList) {
            sb.append(String.format("%d->%d ", task.from, task.to));
        }
        log.info("{}", sb.toString());
        log.info("\n请求次序     服务乘客    电梯移动楼层数");

        this.dispatching();

        int totalTime = 0;
        for (int i = 0; i < taskList.size(); i++) {
            Task task = taskList.get(i);
            int d = Math.abs(task.from - task.to);
            totalTime += d;
            log.info(String.format("   %d     %s:%2d->%2d       -", i, task.name, task.from, task.to));
        }
        log.info("总移动距离: {}", totalTime);
        double ave = totalTime / 1.0 / taskList.size();
        log.info(String.format("平均每次服务的距离: %.1f", ave));
    }

}
