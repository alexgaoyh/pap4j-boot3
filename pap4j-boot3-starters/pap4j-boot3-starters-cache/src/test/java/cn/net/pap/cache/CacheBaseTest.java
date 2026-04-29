package cn.net.pap.cache;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;

import java.net.InetSocketAddress;
import java.net.Socket;

public abstract class CacheBaseTest {

    @BeforeAll
    public static void checkNeo4jAvailable() {
        boolean isUp = false;
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", 6379), 1000);
            isUp = true;
        } catch (Exception e) {
            // connection failed
        }
        Assumptions.assumeTrue(isUp, "redis is not running on 127.0.0.1:6379. Skipping tests.");
    }

}
