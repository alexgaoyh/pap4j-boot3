package cn.net.pap.common.datastructure.observer;

/**
 * 观察者
 */
public interface PapObserver {

    /**
     * 定义一个事件的名称
     * @return
     */
    String _eventName();

    /**
     * 发送通知
     * @param obj
     */
    void callNotify(Object obj);

}
