package cn.net.pap.common.datastructure.collection;

import cn.net.pap.common.datastructure.lamba.StudentDTO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
    @DisplayName("测试：分批执行 (Consumer 场景) - 模拟批量插入")
    void testExecute() {
        List<Integer> mockData = IntStream.rangeClosed(1, 10500).boxed().toList();
        List<Integer> processedData = new ArrayList<>();
        CollectionUtil.batchNoResult(mockData, 2000, batch -> {
            System.out.println("当前批次处理数据量: " + batch.size());
            processedData.addAll(batch);
        });
        Assertions.assertEquals(10500, processedData.size());
    }

    @Test
    @DisplayName("测试：分批查询汇总 (Function 场景) - 模拟巨型 IN 查询")
    void testQuery() {
        List<Long> extremeIds = IntStream.rangeClosed(1, 65000).mapToObj(Long::valueOf).toList();
        List<String> aggregatedResults = CollectionUtil.batchWithResult(extremeIds, 5000, batchIds -> {
            System.out.println("当前批次查询参数量: " + batchIds.size());
            return batchIds.stream().map(id -> "User_" + id).toList();
        });
        Assertions.assertEquals(65000, aggregatedResults.size());
        Assertions.assertEquals("User_1", aggregatedResults.get(0));
        Assertions.assertEquals("User_65000", aggregatedResults.get(64999));
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

    @Test
    public void filterListByListIndexTest() {
        List<Map<String, Object>> leftList = new ArrayList<>();
        leftList.add(CollectionUtil.ofOrdered("id", 1, "name", "D"));
        leftList.add(CollectionUtil.ofOrdered("id", 2, "name", "A"));
        leftList.add(CollectionUtil.ofOrdered("id", 3, "name", "B"));
        leftList.add(CollectionUtil.ofOrdered("id", 4, "name", "C1"));
        leftList.add(CollectionUtil.ofOrdered("id", 5, "name", "C2"));
        leftList.add(CollectionUtil.ofOrdered("id", 6, "name", "C3"));
        leftList.add(CollectionUtil.ofOrdered("id", 7, "name", "C4"));

        List<Map<String, Object>> rightList = new ArrayList<>();
        rightList.add(CollectionUtil.ofOrdered("id", 10, "name", "D0"));
        rightList.add(CollectionUtil.ofOrdered("id", 20, "name", "A0"));
        rightList.add(CollectionUtil.ofOrdered("id", 30, "name", "B0"));
        rightList.add(CollectionUtil.ofOrdered("id", 40, "name", "C01"));
        rightList.add(CollectionUtil.ofOrdered("id", 50, "name", "C02"));
        rightList.add(CollectionUtil.ofOrdered("id", 60, "name", "C03"));
        rightList.add(CollectionUtil.ofOrdered("id", 70, "name", "C04"));

        // 根据 leftList 中元素的下标进行过滤，当 leftList 对应下标的 name 包含字母 "c"（不区分大小写）时，从 rightList 中取出相同下标位置的元素，组成新的列表
        List<Map<String, Object>> rightFilteredList = IntStream.range(0, leftList.size())
                .filter(i -> {
                    Object name = leftList.get(i).get("name");
                    return name instanceof String
                           && ((String) name).toLowerCase().contains("c");
                })
                .filter(i -> i < rightList.size())
                .mapToObj(rightList::get)
                .collect(Collectors.toList());

        System.out.println(rightFilteredList);

    }

    @Test
    public void fillNullKeysTest() {
        List<Map<Integer, String>> dataList = new ArrayList<>();

        dataList.add(CollectionUtil.ofOrderedGeneric(null, "A"));
        dataList.add(CollectionUtil.ofOrderedGeneric(null, "B"));
        dataList.add(CollectionUtil.ofOrderedGeneric(12, "C")); // 物理页锚点
        dataList.add(CollectionUtil.ofOrderedGeneric(null, "D"));
        dataList.add(CollectionUtil.ofOrderedGeneric(15, "E"));

        CollectionUtil.fillNullKeys(dataList);

        Assertions.assertEquals("A", dataList.get(0).get(12));
        Assertions.assertEquals("B", dataList.get(1).get(12));
        Assertions.assertFalse(dataList.get(0).containsKey(null));
        Assertions.assertEquals("D", dataList.get(3).get(15));
        dataList.forEach(System.out::println);
    }

}
