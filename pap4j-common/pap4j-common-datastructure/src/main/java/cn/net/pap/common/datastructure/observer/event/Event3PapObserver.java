package cn.net.pap.common.datastructure.observer.event;

import cn.net.pap.common.datastructure.observer.PapObserver;

/**
 * <p><strong>Event3PapObserver</strong> 处理 <code>event3</code> 通知。</p>
 *
 * <p>这是 {@link PapObserver} 的一个具体实现。</p>
 */
public class Event3PapObserver implements PapObserver {

    /**
     * <p>返回事件名称。</p>
     *
     * @return <strong>"event3"</strong>
     */
    @Override
    public String _eventName() {
        return "event3";
    }

    /**
     * <p>处理传入的通知。</p>
     *
     * @param obj 接收到的对象有效载荷。
     */
    @Override
    public void callNotify(Object obj) {
        System.out.println(this.getClass().getSimpleName() + " 接受到信息" + _eventName() + "，并进行处理 : " + obj);
    }

}