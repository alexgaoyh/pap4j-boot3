package cn.net.pap.common.datastructure.executor;

public interface TaskCallback<T> {

    void onComplete(T result);

}
