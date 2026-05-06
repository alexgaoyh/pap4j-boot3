package cn.net.pap.common.datastructure.designPattern;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class CommandPattern {

    private static final Logger log = LoggerFactory.getLogger(CommandPattern.class);

    interface Order {
        void execute();
    }

    class Stock {

        private String name = "ABC";

        private int quantity = 10;

        public void buy() {
            log.info("Stock [ Name: {}, Quantity: {} ] bought", name, quantity);
        }

        public void sell() {
            log.info("Stock [ Name: {}, Quantity: {} ] sold", name, quantity);
        }
    }

    class BuyStock implements Order {
        private Stock abcStock;

        public BuyStock(Stock abcStock) {
            this.abcStock = abcStock;
        }

        public void execute() {
            abcStock.buy();
        }
    }

    class SellStock implements Order {
        private Stock abcStock;

        public SellStock(Stock abcStock) {
            this.abcStock = abcStock;
        }

        public void execute() {
            abcStock.sell();
        }
    }

    class Broker {
        private List<Order> orderList = new ArrayList<Order>();

        public void takeOrder(Order order) {
            orderList.add(order);
        }

        public void placeOrders() {
            for (Order order : orderList) {
                order.execute();
            }
            orderList.clear();
        }
    }

    @Test
    public void test() {
        Stock abcStock = new Stock();

        BuyStock buyStockOrder = new BuyStock(abcStock);
        SellStock sellStockOrder = new SellStock(abcStock);

        Broker broker = new Broker();
        broker.takeOrder(buyStockOrder);
        broker.takeOrder(sellStockOrder);

        broker.placeOrders();
    }
}
