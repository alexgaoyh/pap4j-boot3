package cn.net.pap.common.excel;

import cn.net.pap.common.excel.dto.CompareDTO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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
}
