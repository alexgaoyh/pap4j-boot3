package cn.net.pap.common.datastructure.jol;

import org.junit.jupiter.api.Test;
import org.openjdk.jol.info.ClassLayout;
import org.openjdk.jol.info.GraphLayout;

public class JolTest {

    static class MyObject {
        int a = 10;
        long b = 123456L;
        String str = "hello";
    }

    @Test
    public void cal() {
        MyObject obj = new MyObject();

        // 打印对象内部结构（对象头、字段对齐等）
        System.out.println("ClassLayout:");
        System.out.println(ClassLayout.parseInstance(obj).toPrintable());

        // 打印对象图总大小（包括引用的对象）
        System.out.println("GraphLayout:");
        System.out.println(GraphLayout.parseInstance(obj).toFootprint());
        System.out.println("Total size: " + GraphLayout.parseInstance(obj).totalSize() + " bytes");
    }

}
