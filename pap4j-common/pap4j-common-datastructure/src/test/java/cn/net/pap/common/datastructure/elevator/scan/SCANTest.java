package cn.net.pap.common.datastructure.elevator.scan;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * 电梯调度
 */
public class SCANTest {

    /**
     * 电梯初始位置
     */
    private static final int INIT_POS = 1;
    /**
     * 最底层
     */
    private static final int MIN_FLOOR = 1;
    /**
     * 最高层
     */
    private static final int MAX_FLOOR = 20;
    /**
     * 电梯初始前进方向
     */
    private static final Direct INIT_DIRECT = Direct.UP;
    /**
     * 请求队列
     */
    private static final List<Task> TASKS = Collections.unmodifiableList(new LinkedList<Task>() {
        {
            add(new Task("乘客1", 4, 7));
            add(new Task("乘客2", 8, 10));
            add(new Task("乘客3", 7, 8));
            add(new Task("乘客4", 9, 2));
            add(new Task("乘客5", 10, 7));
            add(new Task("乘客6", 3, 5));
        }
    });

    @Test
    public void test() {
        SCAN scan = new SCAN(TASKS, INIT_POS, INIT_DIRECT, MIN_FLOOR, MAX_FLOOR);
        scan.exec();
    }

}
