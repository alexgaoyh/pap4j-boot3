package cn.net.pap.example.proguard.publisher.es;

import java.util.List;

public class ElasticSearchSyncEvent<T> {

    private final String index;

    private final SyncType type;

    private final List<T> data;

    public ElasticSearchSyncEvent(String index, SyncType type, List<T> data) {
        this.index = index;
        this.type = type;
        this.data = data;
    }

    public String getIndex() {
        return index;
    }

    public SyncType getType() {
        return type;
    }

    public List<T> getData() {
        return data;
    }

    public enum SyncType {
        CREATE,
        UPDATE,
        DELETE,
        DELCREATE
    }

}
