package cn.net.pap.common.datastructure.observer;

import java.util.ArrayList;
import java.util.List;

/**
 * <p><strong>PapPublisher</strong> 充当 {@link PapSubject} 接口的具体实现。</p>
 *
 * <p>它管理一个 {@link PapObserver} 实例列表并向它们广播通知。 
 * 此外，它支持一种链接机制，以将通知传播到下一层级的主题。</p>
 *
 * <ul>
 *     <li>维护一个观察者注册表。</li>
 *     <li>通过触发后续主题来支持自动化的处理流程。</li>
 * </ul>
 */
public class PapPublisher implements PapSubject {

    /**
     * <p>已订阅观察者的列表。</p>
     */
    private List<PapObserver> observers = new ArrayList<>();

    /**
     * <p>要通知的后续主题列表。</p>
     */
    private List<PapSubject> nextSubjects = new ArrayList<>();

    /**
     * <p>将观察者附加到此发布者。</p>
     *
     * @param o 要附加的 {@link PapObserver}。
     */
    @Override
    public void attach(PapObserver o) {
        observers.add(o);
    }

    /**
     * <p>添加一个后续主题以创建自动化的通知流。</p>
     *
     * @param nextSubject 链中的下一个 {@link PapSubject}。
     */
    @Override
    public void addNextPapSubject(PapSubject nextSubject) {
        nextSubjects.add(nextSubject);
    }

    /**
     * <p>从此发布者中分离一个观察者。</p>
     *
     * @param o 要移除的 {@link PapObserver}。
     */
    @Override
    public void detach(PapObserver o) {
        observers.remove(o);
    }

    /**
     * <p>使用给定数据通知所有附加的观察者和后续主题。</p>
     *
     * @param obj 要广播的数据对象。
     */
    @Override
    public void callNotify(Object obj) {
        observers.forEach(x -> x.callNotify(obj));
        nextSubjects.forEach(x -> x.callNotify(obj));
    }

}