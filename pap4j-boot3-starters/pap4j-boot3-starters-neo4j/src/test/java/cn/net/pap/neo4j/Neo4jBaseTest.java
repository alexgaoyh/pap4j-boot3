package cn.net.pap.neo4j;

import cn.net.pap.common.core.util.NetworkUtil;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;

public abstract class Neo4jBaseTest {

    @BeforeAll
    public static void checkNeo4jAvailable() {
        Assumptions.assumeTrue(NetworkUtil.isPortOpen("127.0.0.1", 7687),
                "Neo4j is not running on 127.0.0.1:7687. Skipping tests.");
    }
}
