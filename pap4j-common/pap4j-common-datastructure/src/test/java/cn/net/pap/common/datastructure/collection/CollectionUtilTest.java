package cn.net.pap.common.datastructure.collection;

import org.junit.jupiter.api.Test;

public class CollectionUtilTest {

    @Test
    public void geneLevelTest() {
        String numbering = "1"; // 初始编号

        System.out.println(numbering); // 输出: 1
        numbering = CollectionUtil.getNextChild(numbering);
        System.out.println(numbering); // 输出: 1.1
        numbering = CollectionUtil.getNextChild(numbering);
        System.out.println(numbering); // 输出: 1.1.1
        numbering = CollectionUtil.getNextSibling(numbering);
        System.out.println(numbering); // 输出: 1.1.2
        numbering = CollectionUtil.getNextSibling(numbering);
        System.out.println(numbering); // 输出: 1.1.3
        numbering = CollectionUtil.exitThenGetNextSibling(numbering);
        System.out.println(numbering); // 输出: 1.2
        numbering = CollectionUtil.exitThenGetNextSibling(numbering);
        System.out.println(numbering); // 输出: 2

    }


}
