package cn.net.pap.common.datastructure.jni;

import org.junit.jupiter.api.Test;

import java.io.File;

public class JNITest {

    @Test
    public void test1() {
        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
        String classpath = System.getProperty("java.class.path");
        String className = JNITest.class.getName();
        System.out.println(javaHome);
        System.out.println(javaBin);
        System.out.println(classpath);
        System.out.println(className);
    }

}
