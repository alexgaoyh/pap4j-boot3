package cn.net.pap.common.datastructure.state;

import java.util.ArrayList;
import java.util.List;

/**
 * 状态流转的状态机
 */
public class StatusFlowStateMachine {

    /**
     * 指定不同的事件名称
     */
    private static final String[] eventTable = {
            "事件1",
            "事件2",
            "事件3",
            "事件4",
            "事件5",
            "事件6",
            "事件7",
            "事件8",
            "事件9",
            "事件10"
    };

    /**
     * 指定流转方式，二位数组的 N*N 与 eventTable 的长度 N 相同
     * -1 代表自身与自身流转，不做任何处理
     * 1 代表接下来的状态被激活
     * 0 代表接下来不可能是这个状态
     *
     * 所以如下的二维数组代表
     *  事件1  后面跟着 事件2
     *  事件2  后面跟着 事件3 事件4 事件5 事件6 事件8
     *  事件3  后面跟着 空
     *  事件4  后面跟着 空
     *  事件5  后面跟着 空
     *  事件6  后面跟着 事件7
     *  事件7  后面跟着 空
     *  事件8  后面跟着 事件9 事件10
     *  事件9  后面跟着 空
     *  事件10 后面跟着 空
     */
    private static final Integer[][] transitionTable = {
            {-1, 1, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, -1, 1, 1, 1, 1, 0, 1, 0, 0},
            {0, 0, -1, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, -1, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, -1, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, -1, 1, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, -1, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, -1, 1, 1},
            {0, 0, 0, 0, 0, 0, 0, 0, -1, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0, -1},
    };

    public static List<String> getNextEventByName(String eventName) {
        List<String> returnList = new ArrayList<>();

        Integer eventLevel = -1;
        for (Integer eventTableIdx = 0; eventTableIdx < eventTable.length; eventTableIdx++) {
            if (eventTable[eventTableIdx].equals(eventName)) {
                eventLevel = eventTableIdx;
                break;
            }
        }
        if (eventLevel != -1) {
            Integer[] rowEvent = transitionTable[eventLevel];
            for (Integer rowEventIdx = 0; rowEventIdx < rowEvent.length; rowEventIdx++) {
                if (rowEvent[rowEventIdx] == 1) {
                    returnList.add(eventTable[rowEventIdx]);
                }
            }
        }

        return returnList;
    }

}
