package cn.net.pap.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 订阅
 */
@Entity
@Table(name = "webhook_subscription")
@Data
public class WebhookSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name; // 订阅名称

    @Column(nullable = false)
    private String callbackUrl; // 回调地址

    @Column(nullable = false)
    private String eventType; // 事件类型

    @Column
    private String secret; // 签名密钥

    @Column(nullable = false)
    private Boolean active = true;

    @Column
    private Integer retryCount = 3; // 重试次数

    @Column
    private Integer timeout = 5000; // 超时时间(ms)

    @CreationTimestamp
    private LocalDateTime createTime;

    @UpdateTimestamp
    private LocalDateTime updateTime;

}
