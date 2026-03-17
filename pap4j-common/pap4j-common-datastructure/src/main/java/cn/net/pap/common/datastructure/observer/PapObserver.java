package cn.net.pap.common.datastructure.observer;

/**
 * <p><strong>PapObserver</strong> 定义了观察者设计模式中观察者的契约。</p>
 *
 * <p>任何希望接收来自 {@link PapSubject} 的变更或事件通知的类都必须实现此接口。</p>
 *
 * <ul>
 *     <li>定义了其监听的具体事件名称。</li>
 *     <li>提供一个用于处理通知的回调方法。</li>
 * </ul>
 */
public interface PapObserver {

    /**
     * <p>返回该观察者感兴趣的事件名称。</p>
     *
     * @return 表示事件名称的字符串。
     */
    String _eventName();

    /**
     * <p>接收通知并处理相关数据。</p>
     *
     * @param obj 主题在通知期间传递的数据对象。
     */
    void callNotify(Object obj);

}