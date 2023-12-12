package cn.net.pap.statemachine.config;

import cn.net.pap.statemachine.enums.OrderEvents;
import cn.net.pap.statemachine.enums.OrderStates;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.data.jpa.JpaPersistingStateMachineInterceptor;
import org.springframework.statemachine.data.jpa.JpaStateMachineRepository;
import org.springframework.statemachine.persist.StateMachineRuntimePersister;

@Configuration
public class OrderJpaPersisterConfig {

    @Bean(name = "orderStateMachineRuntimePersister")
    public StateMachineRuntimePersister<OrderStates, OrderEvents, String> orderStateMachineRuntimePersister(
            JpaStateMachineRepository jpaStateMachineRepository) {
        return new JpaPersistingStateMachineInterceptor<>(jpaStateMachineRepository);
    }
}
