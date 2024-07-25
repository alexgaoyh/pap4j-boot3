package cn.net.pap.common.datastructure.observer;

/**
 * 被观察者
 */
public interface PapSubject {

    void attach(PapObserver a);

    void detach(PapObserver a);

    void callNotify(Object obj);

}
