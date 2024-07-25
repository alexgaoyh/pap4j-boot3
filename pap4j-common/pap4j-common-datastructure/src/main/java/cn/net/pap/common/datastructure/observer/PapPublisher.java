package cn.net.pap.common.datastructure.observer;

import java.util.ArrayList;
import java.util.List;

/**
 * 被观察者 - 维护一个观察者列表.
 */
public class PapPublisher implements PapSubject {

    private List<PapObserver> observers = new ArrayList<>();

    @Override
    public void attach(PapObserver o) {
        observers.add(o);
    }

    @Override
    public void detach(PapObserver o) {
        observers.remove(o);
    }

    @Override
    public void callNotify(Object obj) {
        observers.forEach(x -> x.callNotify(obj));
    }

}
