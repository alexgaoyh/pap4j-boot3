package cn.net.pap.common.datastructure.jni;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class JNITest {

    private static final Logger log = LoggerFactory.getLogger(JNITest.class);

    @Test
    public void test1() {
        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
        String classpath = System.getProperty("java.class.path");
        String className = JNITest.class.getName();
        log.info("{}", javaHome);
        log.info("{}", javaBin);
        log.info("{}", classpath);
        log.info("{}", className);
    }

}
