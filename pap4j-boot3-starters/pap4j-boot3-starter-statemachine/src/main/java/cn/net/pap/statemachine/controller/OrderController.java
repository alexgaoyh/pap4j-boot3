package cn.net.pap.statemachine.controller;

import cn.net.pap.statemachine.entity.Order;
import cn.net.pap.statemachine.enums.OrderEvents;
import cn.net.pap.statemachine.enums.OrderStates;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

    private final StateMachine<OrderStates, OrderEvents> orderStateMachine;

    public OrderController(@Qualifier("orderStateMachine") StateMachine<OrderStates, OrderEvents> orderStateMachine) {
        this.orderStateMachine = orderStateMachine;
    }

    @GetMapping("/created")
    public String created() {
        Order order = new Order();
        order.setId(1l);
        order.setOrderNumber("alexgaoyh");
        order.setStatus(OrderStates.CREATED);

        Message<OrderEvents> orderMessage = MessageBuilder.withPayload(OrderEvents.CREATE_ORDER)
                .setHeader("order", order).build();
        orderStateMachine.sendEvent(orderMessage);

        return "created";
    }

    @GetMapping("/paid")
    public String paid() {
        Order order = new Order();
        order.setId(1l);
        order.setOrderNumber("alexgaoyh");
        order.setStatus(OrderStates.PAID);

        Message<OrderEvents> orderMessage = MessageBuilder.withPayload(OrderEvents.PAY)
                .setHeader("order", order).build();
        orderStateMachine.sendEvent(orderMessage);

        return "paid";
    }

}
