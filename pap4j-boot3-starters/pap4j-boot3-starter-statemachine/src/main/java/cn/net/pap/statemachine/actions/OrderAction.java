package cn.net.pap.statemachine.actions;

import cn.net.pap.statemachine.enums.OrderEvents;
import cn.net.pap.statemachine.enums.OrderStates;
import cn.net.pap.statemachine.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;

@Slf4j
public class OrderAction implements Action<OrderStates, OrderEvents> {

    @Autowired
    private OrderRepository orderRepository;

    private OrderStates source;

    private OrderStates target;

    private OrderEvents event;

    public OrderAction(OrderStates source, OrderStates target, OrderEvents event) {
        this.source = source;
        this.target = target;
        this.event = event;
    }

    @Override
    public void execute(StateContext<OrderStates, OrderEvents> context) {
        System.out.println(orderRepository + " : " + context);
    }
}
