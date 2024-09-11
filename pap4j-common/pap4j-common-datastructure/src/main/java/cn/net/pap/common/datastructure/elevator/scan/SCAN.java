package cn.net.pap.common.datastructure.elevator.scan;

import java.util.ArrayList;
import java.util.List;

/**
 * 电梯调度
 */
public class SCAN {

    private Direct direct;

    private int min;

    private int max;

    private List<Task> taskList;

    private int initPos;

    public SCAN(List<Task> tasks, int initPos, Direct direct, int min, int max) {
        this.taskList = new ArrayList<>(tasks);
        this.initPos = initPos;
        this.direct = direct;
        this.min = min;
        this.max = max;
    }

    protected void dispatching() {
        List<Task> visit = new ArrayList<>(taskList);
        visit.sort(new ScanComparator(initPos, direct));
        taskList.clear();
        taskList.addAll(visit);
    }

    public void exec() {
        System.out.format("电梯当前位于第%s层, 对如下乘客进行服务:\n", initPos);
        for (Task task : taskList) {
            System.out.format("%d->%d ", task.from, task.to);
        }
        System.out.println("\n请求次序     服务乘客    电梯移动楼层数");

        this.dispatching();

        int totalTime = 0;
        for (int i = 0; i < taskList.size(); i++) {
            Task task = taskList.get(i);
            int d = Math.abs(task.from - task.to);
            totalTime += d;
            System.out.format("   %d     %s:%2d->%2d       -\n", i, task.name, task.from, task.to);
        }
        System.out.println("总移动距离: " + totalTime);
        double ave = totalTime / 1.0 / taskList.size();
        System.out.format("平均每次服务的距离: %.1f\n", ave);
    }

}
