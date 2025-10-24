package cn.net.pap.example.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class CreateSubscriptionRequestDTO implements Serializable {

    private String name;

    private String callbackUrl;

    private String eventType;

    private String secret;

}
