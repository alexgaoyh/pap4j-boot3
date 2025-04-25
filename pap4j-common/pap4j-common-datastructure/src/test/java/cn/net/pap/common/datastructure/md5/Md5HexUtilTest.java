package cn.net.pap.common.datastructure.md5;

import org.junit.jupiter.api.Test;

import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class Md5HexUtilTest {

    @Test
    public void checkTest() throws NoSuchAlgorithmException {
        String loginName = "alexgaoyh";
        String password = "pap.net.cn";
        String salt = "pap.net.cn";
        String s = Md5HexUtil.md5Hex(loginName + password + salt);
        assertTrue(s.equals("a235284f528cd69ba4e43fefbde5aebc"));
    }

}
