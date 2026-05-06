package cn.net.pap.common.datastructure.observer.event;

import cn.net.pap.common.datastructure.observer.PapObserver;
import cn.net.pap.common.datastructure.observer.PapSubject;
import cn.net.pap.common.datastructure.observer.event.constant.EventSubjectConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * <p><strong>Event2PapObserver</strong> 处理 <code>event2</code> 通知。</p>
 *
 * <p>此实现包含可能自动触发后续主题（Subject）的逻辑。</p>
 */
public class Event2PapObserver implements PapObserver {

    private static final Logger log = LoggerFactory.getLogger(Event2PapObserver.class);

    /**
     * <p>返回事件名称。</p>
     *
     * @return <strong>"event2"</strong>
     */
    @Override
    public String _eventName() {
        return "event2";
    }

    /**
     * <p>处理传入的通知并根据条件进行转发。</p>
     *
     * @param obj 接收到的对象有效载荷。
     */
    @Override
    public void callNotify(Object obj) {
        log.info("{} 接受到信息{}，并进行处理 : {}", this.getClass().getSimpleName(), _eventName(), obj);
        // todo 这里可以根据实际情况进行调整 ： 是否自动向下做一个触发.
        if(false) {
            Map<String, PapSubject> eventSubjectMap = EventSubjectConstants.eventSubjectMap;
            if(eventSubjectMap.get(_eventName()) != null) {
                eventSubjectMap.get(_eventName()).callNotify(obj);
            }
        }

    }

}