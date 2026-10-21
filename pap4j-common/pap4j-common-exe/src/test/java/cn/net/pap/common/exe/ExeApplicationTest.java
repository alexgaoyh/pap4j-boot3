package cn.net.pap.common.exe;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ExeApplicationTest {

    @Test
    void testRunSuccess() {
        int exitCode = ExeApplication.run(new String[0]);
        Assertions.assertEquals(0, exitCode);
    }

    @Test
    void testRunWithArgs() {
        int exitCode = ExeApplication.run(new String[]{"arg1", "arg2"});
        Assertions.assertEquals(0, exitCode);
    }
}
