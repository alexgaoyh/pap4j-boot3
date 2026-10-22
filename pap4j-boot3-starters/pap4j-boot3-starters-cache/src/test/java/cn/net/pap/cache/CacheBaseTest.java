package cn.net.pap.cache;

import cn.net.pap.common.core.util.NetworkUtil;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;

public abstract class CacheBaseTest {

    @BeforeAll
    public static void checkRedisAvailable() {
        Assumptions.assumeTrue(NetworkUtil.isPortOpen("127.0.0.1", 6379),
                "redis is not running on 127.0.0.1:6379. Skipping tests.");
    }

}
