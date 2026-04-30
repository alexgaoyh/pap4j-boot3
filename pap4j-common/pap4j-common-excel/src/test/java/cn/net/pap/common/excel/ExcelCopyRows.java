package cn.net.pap.common.excel;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ExcelCopyRows {

    @Test
    public void copy() {
        String destFilePath = null;
        try {
            String sourceFilePath = TestResourceUtil.getFile("copy-template.xlsx").getAbsolutePath();
            destFilePath = Files.createTempFile("copy-template-out", ".xlsx").toAbsolutePath().toString();

            // 定义源/模板文件路径，输出文件路径， 明确偏移4行， 偏移后以4行数据为一组，渲染3组。
            ExcelCopyUtil.copyRowWithStyleInGroup(sourceFilePath, destFilePath, "Sheet1", 4, 4, 3);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (destFilePath != null) {
                try {
                    Files.deleteIfExists(Paths.get(destFilePath));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


}
