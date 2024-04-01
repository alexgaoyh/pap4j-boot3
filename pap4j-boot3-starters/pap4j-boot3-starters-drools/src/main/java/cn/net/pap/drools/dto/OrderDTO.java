package cn.net.pap.drools.dto;

import java.io.Serializable;
import java.math.BigDecimal;

public class OrderDTO implements Serializable {

    private BigDecimal price;

    private BigDecimal discount;

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public void setDiscount(BigDecimal discount) {
        this.discount = discount;
    }
}
