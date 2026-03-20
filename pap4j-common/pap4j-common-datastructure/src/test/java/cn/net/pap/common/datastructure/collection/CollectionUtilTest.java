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
        List<Map<String, Object>> rightFilteredList = IntStream.range(0, leftList.size()).filter(i -> {
            Object name = leftList.get(i).get("name");
            return name instanceof String && ((String) name).toLowerCase().contains("c");
        }).filter(i -> i < rightList.size()).mapToObj(rightList::get).collect(Collectors.toList());

        System.out.println(rightFilteredList);

    }

    @Test
    public void fillNullKeysTest() {
        List<Map<Integer, String>> dataList = new ArrayList<>();

        // 纯逻辑段落 / 连续无锚点 说明：通常出现在书本卷首（无物理页对应），或者大段的校勘。
        dataList.add(CollectionUtil.ofOrderedGeneric(null, "A"));
        dataList.add(CollectionUtil.ofOrderedGeneric(null, "B"));

        // 标准单页单段 说明：最理想的数据状态，一个逻辑段落刚好占满一页，或在这一页内完结。
        dataList.add(CollectionUtil.ofOrderedGeneric(12, "C_正常的第12页正文"));

        // 跟随在物理页后的无锚点段落 说明：比如第12页正文结束后，紧跟着一段后人的批注，但该批注在物理图像上没有具体的坐标或页码。
        dataList.add(CollectionUtil.ofOrderedGeneric(null, "D"));

        // 页码逆序 错装（跨多页且顺序错乱）说明：一个长段落跨越了多页，但由于装订错误或扫描顺序出错，逻辑阅读顺序是 14 -> 16 -> 15。
        dataList.add(CollectionUtil.ofOrderedGeneric(14, "E1_逻辑开头", 16, "E2_逻辑中段(物理错装)", 15, "E3_逻辑结尾"));

        // 一页多段（One-to-Many
        dataList.add(CollectionUtil.ofOrderedGeneric(17, "F1_第17页的第一个逻辑段落"));
        dataList.add(CollectionUtil.ofOrderedGeneric(17, "F2_第17页的第二个逻辑段落"));

        // 标准跨页段落 说明：一个逻辑段落从 17 页末尾跨越到了 18 页开头。
        dataList.add(CollectionUtil.ofOrderedGeneric(17, "G1_17页末尾段落起", 18, "G2_18页段落落"));

        //跨页且包含物理空白 图像跳跃 说明：段落从 18 页直接跨到了 20 页。物理第 19 页可能是一张全画幅插图、或者是漏扫了，导致文本锚点断层。
        dataList.add(CollectionUtil.ofOrderedGeneric(18, "H1_18页末尾文字", 20, "H2_直接跳到20页接续的内容"));

        // 夹注与正文高度碎片化 说明：在第 21 页中，正文和双行小注交替出现，它们在物理上属于同一页，但逻辑上被切分成了多个独立的段落。
        dataList.add(CollectionUtil.ofOrderedGeneric(21, "I1_正文第一行"));
        dataList.add(CollectionUtil.ofOrderedGeneric(21, "I2_针对第一行的双行小注"));
        dataList.add(CollectionUtil.ofOrderedGeneric(21, "I3_正文第二行"));

        //混合型（单段落内同时包含物理页和无锚点内容）极端情况。该段落跨越 22 和 23 页，但在两页衔接处，原书有破损，中间的文字是逻辑补全的（null）。
        dataList.add(CollectionUtil.ofOrderedGeneric(22, "J1_第22页清晰文字", null, "J2_原书破损他书补全的文字", 23, "J3_第23页接续文字"));

        CollectionUtil.fillNullKeys(dataList);

        Assertions.assertEquals("A", dataList.get(0).get(12));
        Assertions.assertEquals("B", dataList.get(1).get(12));
        Assertions.assertFalse(dataList.get(0).containsKey(null));
        Assertions.assertEquals("D", dataList.get(3).get(14));
        dataList.forEach(System.out::println);
    }

}
