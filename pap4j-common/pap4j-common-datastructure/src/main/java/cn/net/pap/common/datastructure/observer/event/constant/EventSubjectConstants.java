package cn.net.pap.common.datastructure.observer.event.constant;

import cn.net.pap.common.datastructure.observer.PapObserver;
import cn.net.pap.common.datastructure.observer.PapPublisher;
import cn.net.pap.common.datastructure.observer.PapSubject;
import cn.net.pap.common.datastructure.observer.event.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * <p><strong>EventSubjectConstants</strong> 包含了预先配置的观察者链。</p>
 *
 * <p>它提供了不可变的静态映射（Map），代表着不同的工作流拓扑结构，
 * 将事件映射到其响应的 {@link PapSubject} 层级结构。</p>
 */
public class EventSubjectConstants {

    /**
     * <p>维护了一系列预定义的观察者链。</p>
     * <ul>
     *     <li><strong>1 -> 2 -> 3</strong></li>
     *     <li><strong>1 -> 2 -> 4</strong></li>
     *     <li><strong>1 -> 2 -> 5</strong></li>
     *     <li><strong>1 -> 2 -> 6 -> 7</strong></li>
     *     <li><strong>1 -> 2 -> 8 -> 9</strong></li>
     *     <li><strong>1 -> 2 -> 8 -> 0</strong></li>
     * </ul>
     */
    public static final Map<String, PapSubject> eventSubjectMap = Collections.unmodifiableMap(new HashMap<String, PapSubject>()
    {
        private static final long serialVersionUID = 1L;
        {
            PapObserver event1 = new Event1PapObserver();
            PapObserver event2 = new Event2PapObserver();
            PapObserver event3 = new Event3PapObserver();
            PapObserver event4 = new Event4PapObserver();
            PapObserver event5 = new Event5PapObserver();
            PapObserver event6 = new Event6PapObserver();
            PapObserver event7 = new Event7PapObserver();
            PapObserver event8 = new Event8PapObserver();
            PapObserver event9 = new Event9PapObserver();
            PapObserver event0 = new Event0PapObserver();

            PapSubject p1 = new PapPublisher();
            p1.attach(event2);

            PapSubject p2 = new PapPublisher();
            p2.attach(event3);
            p2.attach(event4);
            p2.attach(event5);
            p2.attach(event6);
            p2.attach(event8);

            PapSubject p6 = new PapPublisher();
            p6.attach(event7);

            PapSubject p8 = new PapPublisher();
            p8.attach(event9);
            p8.attach(event0);

            put("event1", p1);
            put("event2", p2);
            put("event6", p6);
            put("event8", p8);
        }
    });


    /**
     * <p>维护了一个由后续主题链接起来的观察者互联树。</p>
     * <p>仅暴露根节点（event1）。传递给它的通知将自动向下流动。</p>
     * <ul>
     *     <li><strong>1 -> 2 -> 3</strong></li>
     *     <li><strong>1 -> 2 -> 4</strong></li>
     *     <li><strong>1 -> 2 -> 5</strong></li>
     *     <li><strong>1 -> 2 -> 6 -> 7</strong></li>
     *     <li><strong>1 -> 2 -> 8 -> 9</strong></li>
     *     <li><strong>1 -> 2 -> 8 -> 0</strong></li>
     * </ul>
     */
    public static final Map<String, PapSubject> eventSubjectMap2 = Collections.unmodifiableMap(new HashMap<String, PapSubject>()
    {
        private static final long serialVersionUID = 1L;
        {
            PapObserver event1 = new Event1PapObserver();
            PapObserver event2 = new Event2PapObserver();
            PapObserver event3 = new Event3PapObserver();
            PapObserver event4 = new Event4PapObserver();
            PapObserver event5 = new Event5PapObserver();
            PapObserver event6 = new Event6PapObserver();
            PapObserver event7 = new Event7PapObserver();
            PapObserver event8 = new Event8PapObserver();
            PapObserver event9 = new Event9PapObserver();
            PapObserver event0 = new Event0PapObserver();

            PapSubject p1 = new PapPublisher();
            p1.attach(event2);

            PapSubject p2 = new PapPublisher();
            p2.attach(event3);
            p2.attach(event4);
            p2.attach(event5);
            p2.attach(event6);
            p2.attach(event8);

            PapSubject p6 = new PapPublisher();
            p6.attach(event7);

            PapSubject p8 = new PapPublisher();
            p8.attach(event9);
            p8.attach(event0);

            p1.addNextPapSubject(p2);
            p2.addNextPapSubject(p6);
            p2.addNextPapSubject(p8);

            put("event1", p1);
        }
    });

}