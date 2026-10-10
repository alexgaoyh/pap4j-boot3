package cn.net.pap.example.proguard.publisher.es;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ElasticsearchDomainEventPublisher {

    private final ApplicationEventPublisher publisher;

    public ElasticsearchDomainEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    /**
     * 单条
     * @param <T> 泛型参数
     * @param entity
     * @param type
     */
    public <T extends ElasticSearchIndexAware> void publish(
            T entity,
            ElasticSearchSyncEvent.SyncType type) {

        publish(List.of(entity), type);
    }

    /**
     * 批量
     * @param <T> 泛型参数
     * @param entities
     * @param type
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
