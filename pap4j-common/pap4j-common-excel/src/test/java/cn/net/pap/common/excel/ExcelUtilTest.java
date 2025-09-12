package cn.net.pap.common.excel;

import cn.net.pap.common.excel.dto.CompareDTO;
import cn.net.pap.common.excel.dto.ExportDTO;
import cn.net.pap.common.excel.dto.ParentChildDTO;
import cn.net.pap.common.excel.dto.SimpleTriple;
import cn.net.pap.common.excel.handle.ImageModifyHandler;
import cn.net.pap.common.excel.handle.ImageModifyHandler2;
import cn.net.pap.common.excel.jackson.ParentChildDeserializer;
import cn.net.pap.common.excel.jackson.ParentChildSerializer;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.excel.write.metadata.fill.FillConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExcelUtilTest {

    // @Test
    public void extract() {
        String excelAbsolutePath = "C:\\Users\\86181\\Desktop\\test.xlsx";

        List<CompareDTO> compareDTOS = new ArrayList<>();
        compareDTOS.add(new CompareDTO("订单编号", "订单编号"));
        compareDTOS.add(new CompareDTO("收货人", "收货人"));
        compareDTOS.add(new CompareDTO("收货电话", "收货电话"));

        List<Map<String, Object>> oneToManyRowList = ExcelUtil.getOneToManyRowList(excelAbsolutePath, "订单头",
                excelAbsolutePath, "订单明细",
                compareDTOS,
                "cn_net_pap_index",
                "cn_net_pap_child");

        System.out.println(oneToManyRowList);

    }

    // @Test
    public void compareString() {
        String minStr = "!!!!"; // ASCII 33
        String maxStr = "~~~~"; // ASCII 126
        String checkStr = "@@@@";
        assertTrue(checkStr.compareTo(maxStr) <= 0 && checkStr.compareTo(minStr) >= 0);
    }

    // @Test
    public void weeksBetween() {
        LocalDate startDate = LocalDate.of(2024, 2, 5); // 开始日期
        LocalDate endDate = LocalDate.now();

        long weeksBetween = ChronoUnit.WEEKS.between(startDate, endDate);
        System.out.println(weeksBetween);
    }

    // @Test
    public void pictureExport() throws Exception {
        String tempFileName = "template.xlsx";
        InputStream resourceAsStream = new FileInputStream(new File(tempFileName));
        String fileName = "out.xlsx";

        List<ExportDTO> dataList = new ArrayList<>();
        dataList.add(new ExportDTO(new File("3.jpg")));
        dataList.add(new ExportDTO(new File("2.jpg")));
        dataList.add(new ExportDTO(new File("1.jpg")));

        try (ExcelWriter excelWriter = EasyExcel.write(fileName).withTemplate(resourceAsStream).build()) {
            WriteSheet writeSheet0 = EasyExcel.writerSheet(0).registerWriteHandler(new ImageModifyHandler()).build();
            FillConfig fillConfig0 = FillConfig.builder().forceNewRow(Boolean.TRUE).build();
            excelWriter.fill(dataList, fillConfig0, writeSheet0);
        }

    }

    // @Test
    public void pictureExport2() throws Exception {
        // 合并后的单元格，每个单元格定义为  {picture1} {picture2} {picture3}
        String tempFileName = "template.xlsx";
        InputStream resourceAsStream = new FileInputStream(new File(tempFileName));
        String fileName = "out.xlsx";

        try (ExcelWriter excelWriter = EasyExcel.write(fileName).withTemplate(resourceAsStream).build()) {
            WriteSheet writeSheet0 = EasyExcel.writerSheet(0).registerWriteHandler(new ImageModifyHandler2()).build();
            Map<String, Object> map = new HashMap<>();
            map.put("picture1", new File("1.jpg"));
            map.put("picture2", new File("2.jpg"));
            map.put("picture3", new File("3.jpg"));
            excelWriter.fill(map, writeSheet0);
        }

    }

    // @Test
    public void triple() {
        List<Map<String, Object>> rowList = ExcelUtil.getRowListWithMergeCell("merge.xls", "Sheet1", "pap");
        List<SimpleTriple<String, String, Object>> tripleList = ExcelUtil.convert2SimpleTriple(rowList, "名字", new ArrayList<>());
        assertTrue(tripleList.size() > 0);
    }

    /**
     * parent-child.xlsx 文件中有三列： 部门、上级部门、备注， '部门/上级部门'是对应的，‘备注’是记录额外属性.
     */
    // @Test
    public void parentChild() {
        List<Map<String, Object>> rowList = ExcelUtil.getRowList("parent-child.xlsx", "Sheet1", null);
        System.out.println(rowList);
        List<ParentChildDTO> parentChildDTOS = ParentChildDTO.convertToParentChildList(rowList);
        try {
            // 自定义父子关系的 序列化
            ObjectMapper objectMapperSerializer = new ObjectMapper();
            SimpleModule module = new SimpleModule();
            module.addSerializer(ParentChildDTO.class, new ParentChildSerializer());
            objectMapperSerializer.registerModule(module);
            String serializerStr = objectMapperSerializer.writeValueAsString(parentChildDTOS);
            System.out.println(serializerStr);

            // 自定义父子关系的 反序列化
            ObjectMapper objectMapperDeserializer = new ObjectMapper();
            SimpleModule module2 = new SimpleModule();
            module2.addDeserializer(ParentChildDTO.class, new ParentChildDeserializer());
            objectMapperDeserializer.registerModule(module2);
            List<ParentChildDTO> parentChildDTOList = objectMapperDeserializer.readValue(serializerStr, new TypeReference<List<ParentChildDTO>>() {});
            System.out.println(parentChildDTOList);

        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    // @Test
    public void formulaTest() {
        List<Map<String, Object>> sheet2 = ExcelUtil.getRowList("C:\\Users\\86181\\Desktop\\formula.xlsx", "Sheet2", null);
        System.out.println(sheet2);
    }

    // @Test
    public void gerRowListTest() throws Exception {
        String excelAbsolutePath = "C:\\Users\\86181\\Desktop\\input.xlsx";

        List<CompareDTO> compareDTOS = new ArrayList<>();
        compareDTOS.add(new CompareDTO("标识号", "標識號"));

        /**
         * 两个Excel ，封装合并为一个数据集合，关联的字段如上配置所示
         */
        List<Map<String, Object>> oneToManyRowList = ExcelUtil.getOneToManyRowList(excelAbsolutePath, "Sheet1",
                excelAbsolutePath, "Sheet2",
                compareDTOS,
                null,
                "_children");

        ObjectMapper objectMapper = new ObjectMapper();
        System.out.println(objectMapper.writeValueAsString(oneToManyRowList));
    }

    // @Test
    public void findHiddenRowsTest() {
        List<Integer> hiddenRows = ExcelUtil.findHiddenRows("C:\\Users\\86181\\Desktop\\扫描清单(1).xlsx", "Sheet1");
        System.out.println(hiddenRows);
    }

}
