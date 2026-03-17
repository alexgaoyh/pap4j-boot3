package cn.net.pap.common.datastructure.elevator.scan;

/**
 * <h1>电梯运行方向枚举 (Elevator Direction)</h1>
 * <p>表示电梯（或其他调度模拟对象）的物理或逻辑运行方向。</p>
 * <ul>
 *     <li>{@link #UP}: 向上或向增大方向运行</li>
 *     <li>{@link #DOWN}: 向下或向减小方向运行</li>
 * </ul>
 *
 * @author alexgaoyh
 */
public enum Direct {
    /**
     * <p>表示向上运行。</p>
     */
    UP,
    
    /**
     * <p>表示向下运行。</p>
     */
    DOWN
}