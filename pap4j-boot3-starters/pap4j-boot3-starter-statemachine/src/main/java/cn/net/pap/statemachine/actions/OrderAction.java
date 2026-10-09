package cn.net.pap.statemachine.actions;

import cn.net.pap.statemachine.enums.OrderEvents;
import cn.net.pap.statemachine.enums.OrderStates;
import cn.net.pap.statemachine.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;

@Slf4j
public class OrderAction implements Action<OrderStates, OrderEvents> {

    private final OrderRepository orderRepository;

    private OrderStates source;

    private OrderStates target;

    private OrderEvents event;

    /**
     * 创建订单状态机动作。
     *
     * @param orderRepository 订单仓储
     * @param source          源状态
     * @param target          目标状态
     * @param event           触发事件
     */
    public OrderAction(OrderRepository orderRepository, OrderStates source, OrderStates target, OrderEvents event) {
        this.orderRepository = orderRepository;
        this.source = source;
        this.target = target;
        this.event = event;
    }

    @Override
    public void execute(StateContext<OrderStates, OrderEvents> context) {
        log.info("{} : {}", orderRepository, context);
    }
}
