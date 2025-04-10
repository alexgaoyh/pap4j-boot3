package cn.net.pap.example.proguard.listener;

import jakarta.persistence.PostPersist;
import jakarta.persistence.PostUpdate;
import jakarta.persistence.PostRemove;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransactionCompletionListener {

    private static final Logger log = LoggerFactory.getLogger(TransactionCompletionListener.class);

    @PostPersist
    public void onTransactionCompletionPostPersist(Object entity) {
        if(log.isDebugEnabled()) {
            System.out.println("onTransactionCompletionPostPersist : " + entity.toString());
        }
    }

    @PostUpdate
    public void onTransactionCompletionPostUpdate(Object entity) {
        if(log.isDebugEnabled()) {
            System.out.println("onTransactionCompletionPostUpdate : " + entity.toString());
        }
    }

    @PostRemove
    public void onTransactionCompletionPostRemove(Object entity) {
        if(log.isDebugEnabled()) {
            System.out.println("onTransactionCompletionPostRemove : " + entity.toString());
        }
    }

}