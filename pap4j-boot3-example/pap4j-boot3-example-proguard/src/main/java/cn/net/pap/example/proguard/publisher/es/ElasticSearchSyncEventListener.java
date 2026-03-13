package cn.net.pap.example.proguard.publisher.es;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ElasticSearchSyncEventListener {

    private static final Logger log = LoggerFactory.getLogger(ElasticSearchSyncEventListener.class);

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public <T> void onEvent(ElasticSearchSyncEvent<T> event) {

        switch (event.getType()) {
            case UPDATE -> {
                log.info("{} , UPDATE : {}", Thread.currentThread().getName(), event.getIndex());
            }
            case DELETE -> {
                log.info("{} , DELETE : {}", Thread.currentThread().getName(), event.getIndex());
            }
        }

    }

}
