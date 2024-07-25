package cn.net.pap.common.datastructure.observer;

/**
 * 被观察者
 */
public interface PapSubject {

    void attach(PapObserver a);

    void addNextPapSubject(PapSubject nextSubject);

    void detach(PapObserver a);

    void callNotify(Object obj);

}
