package cn.net.pap.neo4j;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import java.net.InetSocketAddress;
import java.net.Socket;

public abstract class Neo4jBaseTest {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Neo4jBaseTest.class);

    @BeforeAll
    public static void checkNeo4jAvailable() {
        boolean isUp = false;
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", 7687), 1000);
            isUp = true;
        } catch (Exception e) {
            log.error("Neo4j is not available", e);
            // connection failed
        }
        Assumptions.assumeTrue(isUp, "Neo4j is not running on 127.0.0.1:7687. Skipping tests.");
    }
}
