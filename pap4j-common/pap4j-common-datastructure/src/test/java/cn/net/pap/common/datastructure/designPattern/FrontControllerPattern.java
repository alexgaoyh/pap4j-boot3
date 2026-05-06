package cn.net.pap.common.datastructure.designPattern;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FrontControllerPattern {

    private static final Logger log = LoggerFactory.getLogger(FrontControllerPattern.class);

    class HomeView {
        public void show() {
            log.info("Displaying Home Page");
        }
    }

    class StudentView {
        public void show() {
            log.info("Displaying Student Page");
        }
    }

    class Dispatcher {
        private StudentView studentView;
        private HomeView homeView;

        public Dispatcher() {
            studentView = new StudentView();
            homeView = new HomeView();
        }

        public void dispatch(String request) {
            if (request.equalsIgnoreCase("STUDENT")) {
                studentView.show();
            } else {
                homeView.show();
            }
        }
    }

    class FrontController {

        private Dispatcher dispatcher;

        public FrontController() {
            dispatcher = new Dispatcher();
        }

        private boolean isAuthenticUser() {
            log.info("User is authenticated successfully.");
            return true;
        }

        private void trackRequest(String request) {
            log.info("Page requested: {}", request);
        }

        public void dispatchRequest(String request) {
            //记录每一个请求
            trackRequest(request);
            //对用户进行身份验证
            if (isAuthenticUser()) {
                dispatcher.dispatch(request);
            }
        }
    }

    @Test
    public void test() {
        FrontController frontController = new FrontController();
        frontController.dispatchRequest("HOME");
        frontController.dispatchRequest("STUDENT");
    }
}
