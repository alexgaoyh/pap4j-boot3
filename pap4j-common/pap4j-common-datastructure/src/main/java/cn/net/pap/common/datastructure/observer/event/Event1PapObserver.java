package cn.net.pap.common.datastructure.observer.event;

import cn.net.pap.common.datastructure.observer.PapObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p><strong>Event1PapObserver</strong> 处理 <code>event1</code> 通知。</p>
 *
 * <p>这是 {@link PapObserver} 的一个具体实现。</p>
 */
public class Event1PapObserver implements PapObserver {

    private static final Logger log = LoggerFactory.getLogger(Event1PapObserver.class);

    /**
     * <p>返回事件名称。</p>
     *
     * @return <strong>"event1"</strong>
     */
    @Override
    public String _eventName() {
        return "event1";
    }

    /**
     * <p>处理传入的通知。</p>
     *
     * @param obj 接收到的对象有效载荷。
     */
    @Override
    public void callNotify(Object obj) {
        log.info("{} 接受到信息{}，并进行处理 : {}", this.getClass().getSimpleName(), _eventName(), obj);
    }

}