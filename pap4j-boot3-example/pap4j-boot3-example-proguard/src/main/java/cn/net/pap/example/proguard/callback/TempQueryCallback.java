package cn.net.pap.example.proguard.callback;

@FunctionalInterface
public interface TempQueryCallback<T> {

    T execute();

}
