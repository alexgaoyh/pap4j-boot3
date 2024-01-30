package cn.net.pap.common.excel;

import org.junit.jupiter.api.Test;

public class ExcelCopyRows {

    // @Test
    public void copy() {
        try {
            String sourceFilePath = "pap.xlsx";
            String destFilePath = "pap-output.xlsx";

            // 定义源/模板文件路径，输出文件路径， 明确偏移4行， 偏移后以4行数据为一组，渲染3组。
            ExcelCopyUtil.copyRowWithStyleInGroup(sourceFilePath, destFilePath, "Sheet1", 4, 4, 3);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
