package cn.net.pap.common.datastructure.catalog;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class CollectionTest {

    /**
     * Arrays test
     */
    @Test
    public void binarySearchTest() {
        String[] strArray = {"pap","net","cn","alexgaoyh"};
        int pap1 = Arrays.binarySearch(strArray, "pap");
        int net1 = Arrays.binarySearch(strArray, "net");
        int cn1 = Arrays.binarySearch(strArray, "cn");
        int alexgaoyh1 = Arrays.binarySearch(strArray, "alexgaoyh");
        assertFalse(pap1 >= 0 && net1 >= 0 && cn1 >= 0 && alexgaoyh1 >= 0);

        Arrays.sort(strArray);
        System.out.println(Arrays.toString(strArray));
        // 在排序状态下的处理.
        int pap2 = Arrays.binarySearch(strArray, "pap");
        int net2 = Arrays.binarySearch(strArray, "net");
        int cn2 = Arrays.binarySearch(strArray, "cn");
        int alexgaoyh2 = Arrays.binarySearch(strArray, "alexgaoyh");
        assertTrue(pap2 >= 0 && net2 >= 0 && cn2 >= 0 && alexgaoyh2 >= 0);

        System.out.println();
    }
}
