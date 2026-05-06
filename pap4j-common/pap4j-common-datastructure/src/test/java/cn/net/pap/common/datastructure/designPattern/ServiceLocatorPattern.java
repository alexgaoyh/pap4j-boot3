package cn.net.pap.common.datastructure.designPattern;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ServiceLocatorPattern {

    private static final Logger log = LoggerFactory.getLogger(ServiceLocatorPattern.class);

    interface Service {
        public String getName();

        public void execute();
    }

    static class Service1 implements Service {
        public void execute() {
            log.info("Executing Service1");
        }

        @Override
        public String getName() {
            return "Service1";
        }
    }

    static class Service2 implements Service {
        public void execute() {
            log.info("Executing Service2");
        }

        @Override
        public String getName() {
            return "Service2";
        }
    }

    static class InitialContext {
        public Object lookup(String jndiName) {
            if (jndiName.equalsIgnoreCase("SERVICE1")) {
                log.info("Looking up and creating a new Service1 object");
                return new Service1();
            } else if (jndiName.equalsIgnoreCase("SERVICE2")) {
                log.info("Looking up and creating a new Service2 object");
                return new Service2();
            }
            return null;
        }
    }

    static class Cache {

        private List<Service> services;

        public Cache() {
            services = new ArrayList<Service>();
        }

        public Service getService(String serviceName) {
            for (Service service : services) {
                if (service.getName().equalsIgnoreCase(serviceName)) {
                    log.info("Returning cached {} object", serviceName);
                    return service;
                }
            }
            return null;
        }

        public void addService(Service newService) {
            boolean exists = false;
            for (Service service : services) {
                if (service.getName().equalsIgnoreCase(newService.getName())) {
                    exists = true;
                }
            }
            if (!exists) {
                services.add(newService);
            }
        }
    }

    static class ServiceLocator {
        private static Cache cache;

        static {
            cache = new Cache();
        }

        public static Service getService(String jndiName) {

            Service service = cache.getService(jndiName);

            if (service != null) {
                return service;
            }

            InitialContext context = new InitialContext();
            Service service1 = (Service) context.lookup(jndiName);
            cache.addService(service1);
            return service1;
        }
    }

    @Test
    public void test() {
        Service service = ServiceLocator.getService("Service1");
        service.execute();
        service = ServiceLocator.getService("Service2");
        service.execute();
        service = ServiceLocator.getService("Service1");
        service.execute();
        service = ServiceLocator.getService("Service2");
        service.execute();
    }

}
