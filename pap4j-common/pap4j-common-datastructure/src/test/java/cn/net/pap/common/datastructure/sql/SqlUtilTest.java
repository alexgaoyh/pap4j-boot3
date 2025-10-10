package cn.net.pap.common.datastructure.sql;

import cn.net.pap.common.datastructure.exception.UtilException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SqlUtilTest {

    @Test
    public void test1() {
        assertEquals("id", SqlUtil.checkSql("id"));
        assertEquals("name", SqlUtil.checkSql("name"));
        assertEquals("create_time", SqlUtil.checkSql("create_time"));
        assertEquals("user_name", SqlUtil.checkSql("user_name"));
    }

    @Test
    public void test2() {
        assertEquals("id ASC", SqlUtil.checkSql("id ASC"));
        assertEquals("name DESC", SqlUtil.checkSql("name DESC"));
        assertEquals("id,name", SqlUtil.checkSql("id,name"));
        assertEquals("create_time,update_time", SqlUtil.checkSql("create_time,update_time"));
    }

    @Test
    public void test3() {
        UtilException exception1 = assertThrows(UtilException.class, () -> {
            SqlUtil.checkSql("id; DROP TABLE users");
        });
        assertEquals(exception1.getMessage(), "参数不符合规范，不能进行查询");

        UtilException exception2 = assertThrows(UtilException.class, () -> {
            SqlUtil.checkSql("id ' DROP TABLE users");
        });
        assertEquals(exception2.getMessage(), "参数不符合规范，不能进行查询");

        UtilException exception3 = assertThrows(UtilException.class, () -> {
            SqlUtil.checkSql("id update ");
        });
        assertEquals(exception3.getMessage(), "参数不符合规范，不能进行查询");
    }

}
