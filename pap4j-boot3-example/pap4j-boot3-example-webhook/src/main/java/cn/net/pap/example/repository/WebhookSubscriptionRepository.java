package cn.net.pap.example.repository;

import cn.net.pap.example.entity.WebhookSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface WebhookSubscriptionRepository extends JpaRepository<WebhookSubscription, Long>, JpaSpecificationExecutor<WebhookSubscription> {

    List<WebhookSubscription> findByEventTypeAndActiveTrue(String eventType);


}