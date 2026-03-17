package cn.net.pap.common.datastructure.observer;

/**
 * <p><strong>PapSubject</strong> 定义了观察者（Observer）设计模式中主题（可观察对象）的契约。</p>
 *
 * <p>它提供了管理订阅以及向其观察者广播通知的方法。</p>
 *
 * <ul>
 *     <li>附加/分离观察者。</li>
 *     <li>通知所有已注册的观察者。</li>
 *     <li>链接到其他主题。</li>
 * </ul>
 */
public interface PapSubject {

    /**
     * <p>注册一个观察者以接收通知。</p>
     *
     * @param a 要附加的 {@link PapObserver}。
     */
    void attach(PapObserver a);

    /**
     * <p>链接另一个主题以形成通知链。</p>
     *
     * @param nextSubject 要通知的下一个 {@link PapSubject}。
     */
    void addNextPapSubject(PapSubject nextSubject);

    /**
     * <p>移除一个先前注册的观察者。</p>
     *
     * @param a 要分离的 {@link PapObserver}。
     */
    void detach(PapObserver a);

    /**
     * <p>触发对所有已注册观察者的通知。</p>
     *
     * @param obj 要传递的消息或数据。
     */
    void callNotify(Object obj);

}