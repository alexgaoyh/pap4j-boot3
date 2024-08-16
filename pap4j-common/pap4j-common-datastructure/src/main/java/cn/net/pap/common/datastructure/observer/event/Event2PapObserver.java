package cn.net.pap.common.datastructure.observer.event;

import cn.net.pap.common.datastructure.observer.PapObserver;
import cn.net.pap.common.datastructure.observer.PapSubject;
import cn.net.pap.common.datastructure.observer.event.constant.EventSubjectConstants;

import java.util.Map;

public class Event2PapObserver implements PapObserver {

    @Override
    public String _eventName() {
        return "event2";
    }

    @Override
    public void callNotify(Object obj) {
        System.out.println(this.getClass().getSimpleName() + " 接受到信息" + _eventName() + "，并进行处理 : " + obj);
        // todo 这里可以根据实际情况进行调整 ： 是否自动向下做一个触发.
        if(false) {
            Map<String, PapSubject> eventSubjectMap = EventSubjectConstants.eventSubjectMap;
            if(eventSubjectMap.get(_eventName()) != null) {
                eventSubjectMap.get(_eventName()).callNotify(obj);
            }
        }

    }

}
