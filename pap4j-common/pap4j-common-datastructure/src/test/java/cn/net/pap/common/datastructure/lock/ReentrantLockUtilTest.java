package cn.net.pap.common.datastructure.lock;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReentrantLockUtilTest {

    private static final Logger log = LoggerFactory.getLogger(ReentrantLockUtilTest.class);

    // @Test
    public void lockTest() {
        ReentrantLockUtil.TokenDTO tokenDTO = null;

        for(int i = 0; i < 100; i++) {
            tokenDTO = ReentrantLockUtil.getToken(tokenDTO);
            log.info("{}", tokenDTO);
        }

    }


}
