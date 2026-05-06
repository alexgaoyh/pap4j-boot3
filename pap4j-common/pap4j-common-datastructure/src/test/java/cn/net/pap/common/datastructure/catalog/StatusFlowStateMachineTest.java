package cn.net.pap.common.datastructure.catalog;

import cn.net.pap.common.datastructure.state.StatusFlowStateMachine;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class StatusFlowStateMachineTest {

    private static final Logger log = LoggerFactory.getLogger(StatusFlowStateMachineTest.class);

    @Test
    public void getAllPathTest() {
        List<List<String>> allPath = StatusFlowStateMachine.getAllPath();
        log.info("{}", allPath);
    }

    @Test
    public void getPathsFromRootToLeafTest() {
        List<List<String>> allPath = StatusFlowStateMachine.getPathsFromRootToLeaf();
        log.info("{}", allPath);
    }

    @Test
    public void getNextEventByNameTest() {
        log.info("{}", StatusFlowStateMachine.getNextEventByName("事件1"));
        log.info("{}", StatusFlowStateMachine.getNextEventByName("事件2"));
        log.info("{}", StatusFlowStateMachine.getNextEventByName("事件3"));
        log.info("{}", StatusFlowStateMachine.getNextEventByName("事件4"));
        log.info("{}", StatusFlowStateMachine.getNextEventByName("事件5"));
        log.info("{}", StatusFlowStateMachine.getNextEventByName("事件6"));
        log.info("{}", StatusFlowStateMachine.getNextEventByName("事件7"));
        log.info("{}", StatusFlowStateMachine.getNextEventByName("事件8"));
        log.info("{}", StatusFlowStateMachine.getNextEventByName("事件9"));
        log.info("{}", StatusFlowStateMachine.getNextEventByName("事件10"));

        log.info("-----------------------------------------------------------");

        log.info("{}", StatusFlowStateMachine.getBeforeEventByName("事件1"));
        log.info("{}", StatusFlowStateMachine.getBeforeEventByName("事件2"));
        log.info("{}", StatusFlowStateMachine.getBeforeEventByName("事件3"));
        log.info("{}", StatusFlowStateMachine.getBeforeEventByName("事件4"));
        log.info("{}", StatusFlowStateMachine.getBeforeEventByName("事件5"));
        log.info("{}", StatusFlowStateMachine.getBeforeEventByName("事件6"));
        log.info("{}", StatusFlowStateMachine.getBeforeEventByName("事件7"));
        log.info("{}", StatusFlowStateMachine.getBeforeEventByName("事件8"));
        log.info("{}", StatusFlowStateMachine.getBeforeEventByName("事件9"));
        log.info("{}", StatusFlowStateMachine.getBeforeEventByName("事件10"));
    }
}
