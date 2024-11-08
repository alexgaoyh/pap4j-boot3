package cn.net.pap.common.datastructure.designPattern;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class ServiceLocatorPattern {

    interface Service {
        public String getName();

        public void execute();
    }

    static class Service1 implements Service {
        public void execute() {
            System.out.println("Executing Service1");
        }

        @Override
        public String getName() {
            return "Service1";
        }
    }

    static class Service2 implements Service {
        public void execute() {
            System.out.println("Executing Service2");
        }

        @Override
        public String getName() {
            return "Service2";
        }
    }

    static class InitialContext {
        public Object lookup(String jndiName) {
            if (jndiName.equalsIgnoreCase("SERVICE1")) {
                System.out.println("Looking up and creating a new Service1 object");
                return new Service1();
            } else if (jndiName.equalsIgnoreCase("SERVICE2")) {
                System.out.println("Looking up and creating a new Service2 object");
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
                    System.out.println("Returning cached  " + serviceName + " object");
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
