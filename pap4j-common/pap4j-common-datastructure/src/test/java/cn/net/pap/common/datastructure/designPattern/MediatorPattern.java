package cn.net.pap.common.datastructure.designPattern;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

public class MediatorPattern {

    private static final Logger log = LoggerFactory.getLogger(MediatorPattern.class);

    class User {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public User(String name) {
            this.name = name;
        }

        public void sendMessage(String message) {
            ChatRoom.showMessage(this, message);
        }
    }

    class ChatRoom {
        public static void showMessage(User user, String message) {
            log.info("{} [{}] : {}", new Date().toString(), user.getName(), message);
        }
    }

    @Test
    public void test() {
        User robert = new User("Robert");
        User john = new User("John");

        robert.sendMessage("Hi! John!");
        john.sendMessage("Hello! Robert!");
    }
}
