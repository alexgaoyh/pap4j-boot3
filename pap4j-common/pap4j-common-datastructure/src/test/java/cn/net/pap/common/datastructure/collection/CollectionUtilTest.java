package cn.net.pap.common.datastructure.collection;

import cn.net.pap.common.datastructure.lamba.StudentDTO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    // @Test
    public void batchByPropertyTest() {
        List<StudentDTO> batchEntityList = new ArrayList<>();
        for (int i = 0; i < 9876; i++) {
            StudentDTO studentDTO = new StudentDTO.Builder().setFirstName("alex" + i).setLastName("gaoyh" + i).setAge(i).setEmail("https://pap-docs.pap.net.cn/").build();
            batchEntityList.add(studentDTO);
        }
        List<List<Integer>> ageGroupList = CollectionUtil.batchByProperty(batchEntityList, 50, StudentDTO::getAge);
        List<List<String>> firstNameGroupList = CollectionUtil.batchByProperty(batchEntityList, 50, StudentDTO::getFirstName);
        System.out.println(ageGroupList);
        System.out.println(firstNameGroupList);

        System.out.println("------------------------------------------");

        List<Map<String, Object>> batchMapList = new ArrayList<>();
        for (int i = 0; i < 9876; i++) {
            Map<String, Object> tmp = new HashMap<>();
            tmp.put("firstName", "alex" + i);
            tmp.put("age", i);
            batchMapList.add(tmp);
        }
        List<List<Integer>> ageGroupList2 = CollectionUtil.batchByProperty(batchMapList, 50, map -> (Integer) map.get("age"));
        List<List<String>> firstNameGroupList2 = CollectionUtil.batchByProperty(batchMapList, 50, map -> (String) map.get("firstName"));
        System.out.println(ageGroupList2);
        System.out.println(firstNameGroupList2);


    }

    @Test
    public void sortMapListTest() {
        List<Map<String, Object>> list = new ArrayList<>();

        list.add(Map.of("id", 5, "name", "D"));
        list.add(Map.of("id", 3, "name", "A"));
        list.add(Map.of("id", 1, "name", "B"));
        list.add(Map.of("id", 2, "name", "C"));

        List<Integer> orderList = Arrays.asList(2, 1, 3);

        CollectionUtil.sortByOrderList(list, orderList, "id");

        System.out.println(list);
    }

}
