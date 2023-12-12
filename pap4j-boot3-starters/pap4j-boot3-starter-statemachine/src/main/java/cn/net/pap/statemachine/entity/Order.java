package cn.net.pap.statemachine.entity;

import cn.net.pap.statemachine.enums.OrderStates;
import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "pap_order")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String orderNumber;

    @Enumerated(EnumType.STRING)
    private OrderStates status;

}
