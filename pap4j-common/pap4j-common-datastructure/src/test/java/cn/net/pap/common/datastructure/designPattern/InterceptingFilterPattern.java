package cn.net.pap.common.datastructure.designPattern;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class InterceptingFilterPattern {

    private static final Logger log = LoggerFactory.getLogger(InterceptingFilterPattern.class);

    interface Filter {
        public void execute(String request);
    }

    class AuthenticationFilter implements Filter {
        public void execute(String request) {
            log.info("Authenticating request: {}", request);
        }
    }

    class DebugFilter implements Filter {
        public void execute(String request) {
            log.info("request log: {}", request);
        }
    }

    class Target {
        public void execute(String request) {
            log.info("Executing request: {}", request);
        }
    }

    class FilterChain {
        private List<Filter> filters = new ArrayList<Filter>();
        private Target target;

        public void addFilter(Filter filter) {
            filters.add(filter);
        }

        public void execute(String request) {
            for (Filter filter : filters) {
                filter.execute(request);
            }
            target.execute(request);
        }

        public void setTarget(Target target) {
            this.target = target;
        }
    }

    class FilterManager {
        FilterChain filterChain;

        public FilterManager(Target target) {
            filterChain = new FilterChain();
            filterChain.setTarget(target);
        }

        public void setFilter(Filter filter) {
            filterChain.addFilter(filter);
        }

        public void filterRequest(String request) {
            filterChain.execute(request);
        }
    }

    class Client {
        FilterManager filterManager;

        public void setFilterManager(FilterManager filterManager) {
            this.filterManager = filterManager;
        }

        public void sendRequest(String request) {
            filterManager.filterRequest(request);
        }
    }

    @Test
    public void test() {
        FilterManager filterManager = new FilterManager(new Target());
        filterManager.setFilter(new AuthenticationFilter());
        filterManager.setFilter(new DebugFilter());

        Client client = new Client();
        client.setFilterManager(filterManager);
        client.sendRequest("HOME");
    }

}
