package cn.net.pap.common.datastructure.observer.event;

import cn.net.pap.common.datastructure.observer.PapObserver;

public class Event9PapObserver implements PapObserver {

    @Override
    public String _eventName() {
        return "event9";
    }

    @Override
    public void callNotify(Object obj) {
        System.out.println(this.getClass().getSimpleName() + " 接受到信息" + _eventName() + "，并进行处理 : " + obj);
    }

}
