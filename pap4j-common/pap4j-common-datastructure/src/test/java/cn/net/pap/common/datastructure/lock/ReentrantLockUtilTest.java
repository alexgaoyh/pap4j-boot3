package cn.net.pap.common.datastructure.lock;

import org.junit.jupiter.api.Test;

public class ReentrantLockUtilTest {

    // @Test
    public void lockTest() {
        ReentrantLockUtil.TokenDTO tokenDTO = null;

        for(int i = 0; i < 100; i++) {
            tokenDTO = ReentrantLockUtil.getToken(tokenDTO);
            System.out.println(tokenDTO);
        }

    }


}
