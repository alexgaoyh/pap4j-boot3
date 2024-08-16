package cn.net.pap.common.datastructure.observer.event;

import cn.net.pap.common.datastructure.observer.PapObserver;

public class Event0PapObserver implements PapObserver {

    @Override
    public String _eventName() {
        return "event0";
    }

    @Override
    public void callNotify(Object obj) {
        System.out.println(this.getClass().getSimpleName() + " 接受到信息" + _eventName() + "，并进行处理 : " + obj);
    }

}
