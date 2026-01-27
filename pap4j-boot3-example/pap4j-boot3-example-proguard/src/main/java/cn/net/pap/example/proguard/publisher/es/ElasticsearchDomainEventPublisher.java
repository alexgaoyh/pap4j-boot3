package cn.net.pap.example.proguard.publisher.es;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ElasticsearchDomainEventPublisher {

    @Autowired
    private ApplicationEventPublisher publisher;

    /**
     * 单条
     */
    public <T extends ElasticSearchIndexAware> void publish(
            T entity,
            ElasticSearchSyncEvent.SyncType type) {

        publish(List.of(entity), type);
    }

    /**
     * 批量
     */
    public <T extends ElasticSearchIndexAware> void publish(
            List<T> entities,
            ElasticSearchSyncEvent.SyncType type) {

        if (entities == null || entities.isEmpty()) {
            return;
        }

        String index = entities.get(0).esIndex();

        publisher.publishEvent(
                new ElasticSearchSyncEvent<>(index, type, entities)
        );
    }

}
