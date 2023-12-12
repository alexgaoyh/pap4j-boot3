package cn.net.pap.statemachine.config;

import cn.net.pap.statemachine.actions.ErrorAction;
import cn.net.pap.statemachine.actions.OrderAction;
import cn.net.pap.statemachine.enums.OrderEvents;
import cn.net.pap.statemachine.enums.OrderStates;
import cn.net.pap.statemachine.listeners.OrderStateMachineListener;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.EnableStateMachine;
import org.springframework.statemachine.config.StateMachineBuilder;
import org.springframework.statemachine.persist.StateMachineRuntimePersister;

import java.util.EnumSet;

@Configuration
@EnableStateMachine
public class OrderStateMachineConfiguration {

    @Autowired
    private BeanFactory beanFactory;

    @Autowired
    @Qualifier("orderStateMachineRuntimePersister")
    private StateMachineRuntimePersister<OrderStates, OrderEvents, String> orderStateMachineRuntimePersister;

    @Bean(name = "orderStateMachine")
    StateMachine<OrderStates, OrderEvents> orderStateMachine() throws Exception {
        StateMachineBuilder.Builder<OrderStates, OrderEvents> builder = StateMachineBuilder.builder();

        builder.configureConfiguration()
                .withPersistence().runtimePersister(orderStateMachineRuntimePersister)
                .and()
                .withConfiguration()
                .machineId("orderSingleMachine")
                .autoStartup(true)
                .listener(new OrderStateMachineListener())
                .beanFactory(beanFactory);

        builder.configureStates()
                .withStates()
                .initial(OrderStates.SUBMITTED)
                .end(OrderStates.FULFILLED)
                .end(OrderStates.CANCELLED)
                .states(EnumSet.allOf(OrderStates.class));

        builder.configureTransitions()
                .withExternal().source(OrderStates.SUBMITTED)
                .target(OrderStates.CREATED)
                .event(OrderEvents.CREATE_ORDER)
                .action(createOrderAction(), errorAction())
                .and()
                .withExternal().source(OrderStates.CREATED)
                .target(OrderStates.PAID)
                .event(OrderEvents.PAY)
                .action(payOrderAction(), errorAction())
                .and()
                .withExternal().source(OrderStates.PAID)
                .target(OrderStates.FULFILLED)
                .event(OrderEvents.FULFILL)
                .action(fulfillOrderAction(), errorAction())
                .and()
                .withExternal().source(OrderStates.SUBMITTED)
                .target(OrderStates.CANCELLED)
                .event(OrderEvents.CANCEL)
                .action(submittedCancelOrderAction(), errorAction())
                .and()
                .withExternal().source(OrderStates.PAID)
                .target(OrderStates.CANCELLED)
                .event(OrderEvents.CANCEL)
                .action(paidCancelOrderAction(), errorAction())
                .and()
                .withExternal().source(OrderStates.FULFILLED)
                .target(OrderStates.CANCELLED)
                .event(OrderEvents.CANCEL);

        return builder.build();
    }

    @Bean
    public OrderAction createOrderAction() {
        return new OrderAction(OrderStates.SUBMITTED, OrderStates.CREATED, OrderEvents.CREATE_ORDER);
    }

    @Bean
    public OrderAction payOrderAction() {
        return new OrderAction(OrderStates.CREATED, OrderStates.PAID, OrderEvents.PAY);
    }

    @Bean
    public OrderAction fulfillOrderAction() {
        return new OrderAction(OrderStates.PAID, OrderStates.FULFILLED, OrderEvents.FULFILL);
    }

    @Bean
    public OrderAction submittedCancelOrderAction() {
        return new OrderAction(OrderStates.SUBMITTED, OrderStates.CANCELLED, OrderEvents.CANCEL);
    }

    @Bean
    public OrderAction paidCancelOrderAction() {
        return new OrderAction(OrderStates.PAID, OrderStates.CANCELLED, OrderEvents.CANCEL);
    }

    @Bean
    public OrderAction fulfilledCancelOrderAction() {
        return new OrderAction(OrderStates.FULFILLED, OrderStates.CANCELLED, OrderEvents.CANCEL);
    }

    @Bean
    public ErrorAction errorAction() {
        return new ErrorAction();
    }
}
