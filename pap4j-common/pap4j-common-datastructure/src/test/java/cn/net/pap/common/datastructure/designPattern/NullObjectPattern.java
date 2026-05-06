package cn.net.pap.common.datastructure.designPattern;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NullObjectPattern {

    private static final Logger log = LoggerFactory.getLogger(NullObjectPattern.class);

    static abstract class AbstractCustomer {
        protected String name;

        public abstract boolean isNil();

        public abstract String getName();
    }

    static class RealCustomer extends AbstractCustomer {

        public RealCustomer(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isNil() {
            return false;
        }
    }

    static class NullCustomer extends AbstractCustomer {

        @Override
        public String getName() {
            return "Not Available in Customer Database";
        }

        @Override
        public boolean isNil() {
            return true;
        }
    }

    class CustomerFactory {

        public static final String[] names = {"Rob", "Joe", "Julie"};

        public static AbstractCustomer getCustomer(String name) {
            for (int i = 0; i < names.length; i++) {
                if (names[i].equalsIgnoreCase(name)) {
                    return new RealCustomer(name);
                }
            }
            return new NullCustomer();
        }
    }

    @Test
    public void test() {

        AbstractCustomer customer1 = CustomerFactory.getCustomer("Rob");
        AbstractCustomer customer2 = CustomerFactory.getCustomer("Bob");
        AbstractCustomer customer3 = CustomerFactory.getCustomer("Julie");
        AbstractCustomer customer4 = CustomerFactory.getCustomer("Laura");

        log.info("Customers");
        log.info(customer1.getName());
        log.info(customer2.getName());
        log.info(customer3.getName());
        log.info(customer4.getName());
    }

}
