package cn.net.pap.common.datastructure.catalog;

import cn.net.pap.common.datastructure.state.StatusFlowStateMachine;
import org.junit.jupiter.api.Test;

public class StatusFlowStateMachineTest {

    @Test
    public void getNextEventByNameTest() {
        System.out.println(StatusFlowStateMachine.getNextEventByName("事件1"));
        System.out.println(StatusFlowStateMachine.getNextEventByName("事件2"));
        System.out.println(StatusFlowStateMachine.getNextEventByName("事件3"));
        System.out.println(StatusFlowStateMachine.getNextEventByName("事件4"));
        System.out.println(StatusFlowStateMachine.getNextEventByName("事件5"));
        System.out.println(StatusFlowStateMachine.getNextEventByName("事件6"));
        System.out.println(StatusFlowStateMachine.getNextEventByName("事件7"));
        System.out.println(StatusFlowStateMachine.getNextEventByName("事件8"));
        System.out.println(StatusFlowStateMachine.getNextEventByName("事件9"));
        System.out.println(StatusFlowStateMachine.getNextEventByName("事件10"));
    }
}
