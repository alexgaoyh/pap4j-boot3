package cn.net.pap.common.datastructure.catalog;

import cn.net.pap.common.datastructure.observer.*;
import cn.net.pap.common.datastructure.observer.event.Event1PapObserver;
import cn.net.pap.common.datastructure.observer.event.constant.EventSubjectConstants;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

public class PapObserverTest {

    @Test
    public void test1() {
        Map<String, PapSubject> eventSubjectMap = EventSubjectConstants.eventSubjectMap;
        for(int i = 0; i < 10; i++) {
            if(eventSubjectMap.containsKey("event" + i)) {
                eventSubjectMap.get("event" + i).callNotify("event " + i + " finish, call next");
            }
            System.out.println("-------------------------------------------------");
        }

    }

    /**
     * 类似 观察者+责任链
     */
    @Test
    public void test2() {
        Map<String, PapSubject> eventSubjectMap2 = EventSubjectConstants.eventSubjectMap2;
        for(Map.Entry<String, PapSubject> entry : eventSubjectMap2.entrySet()) {
            entry.getValue().callNotify(entry.getKey() + " finish, call next");
        }
    }


    @Test
    public void test3() {
        PapObserver event1 = new Event1PapObserver();

        PapSubject p1 = new PapPublisher();
        p1.attach(event1);

        Map<String, PapSubject> eventSubjectMap = new HashMap<>();
        eventSubjectMap.put("event1", p1);

        for(Map.Entry<String, PapSubject> entry : eventSubjectMap.entrySet()) {
            entry.getValue().callNotify(entry.getKey() + " finish, call next");
        }

        for(Map.Entry<String, PapSubject> entry : eventSubjectMap.entrySet()) {
            entry.getValue().detach(event1);
            entry.getValue().callNotify(entry.getKey() + " uncalled");
        }
    }

}
