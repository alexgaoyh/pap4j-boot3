package cn.net.pap.common.datastructure.elevator.scan;

/**
 * <h1>电梯乘车请求任务 (Elevator Task)</h1>
 * <p>表示调度系统中单个乘客的乘车请求，包含乘客名字、出发楼层及目的楼层。</p>
 *
 * @author alexgaoyh
 */
public class Task {

    /**
     * <p>乘客姓名或任务标识名称。</p>
     */
    public String name;

    /**
     * <p>请求发生的所在楼层（起始楼层）。</p>
     */
    public int from;

    /**
     * <p>请求期望到达的目的楼层（目标楼层）。</p>
     */
    public int to;

    /**
     * <p>构造一个乘车任务。</p>
     *
     * @param name 乘客姓名
     * @param from 请求所在楼层
     * @param to   请求去往楼层
     */
    public Task(String name, int from, int to) {
        this.name = name;
        this.from = from;
        this.to = to;
    }

    /**
     * <p>获取该乘客期望电梯移动的相对运行方向。</p>
     *
     * @return 如果目标楼层低于出发楼层，返回 {@link Direct#DOWN}，否则返回 {@link Direct#UP}。
     */
    public Direct getDirect() {
        return from - to > 0 ? Direct.DOWN : Direct.UP;
    }

    /**
     * <p>返回描述任务的文本字符串。</p>
     * <strong>示例:</strong> {@code "John 3->5"}
     *
     * @return 乘车任务说明文本
     */
    @Override
    public String toString() {
        return String.format("%s %d->%d", name, from, to);
    }

}
