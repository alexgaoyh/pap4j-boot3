package cn.net.pap.example.proguard.listener;

import jakarta.persistence.PostPersist;
import jakarta.persistence.PostUpdate;
import jakarta.persistence.PostRemove;

public class TransactionCompletionListener {

    @PostPersist
    public void onTransactionCompletionPostPersist(Object entity) {
        System.out.println("onTransactionCompletionPostPersist : " + entity.toString());
    }

    @PostUpdate
    public void onTransactionCompletionPostUpdate(Object entity) {
        System.out.println("onTransactionCompletionPostUpdate : " + entity.toString());
    }

    @PostRemove
    public void onTransactionCompletionPostRemove(Object entity) {
        System.out.println("onTransactionCompletionPostRemove : " + entity.toString());
    }

}