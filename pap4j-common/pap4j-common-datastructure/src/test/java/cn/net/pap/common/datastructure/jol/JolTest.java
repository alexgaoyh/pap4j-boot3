package cn.net.pap.common.datastructure.jol;

import org.junit.jupiter.api.Test;
import org.openjdk.jol.info.ClassLayout;
import org.openjdk.jol.info.GraphLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JolTest {

    private static final Logger log = LoggerFactory.getLogger(JolTest.class);

    static class MyObject {
        int a = 10;
        long b = 123456L;
        String str = "hello";
    }

    @Test
    public void cal() {
        MyObject obj = new MyObject();

        // 打印对象内部结构（对象头、字段对齐等）
        log.info("ClassLayout:");
        log.info("{}", ClassLayout.parseInstance(obj).toPrintable());

        // 打印对象图总大小（包括引用的对象）
        log.info("GraphLayout:");
        log.info("{}", GraphLayout.parseInstance(obj).toFootprint());
        log.info("Total size: {} bytes", GraphLayout.parseInstance(obj).totalSize());
    }

}
