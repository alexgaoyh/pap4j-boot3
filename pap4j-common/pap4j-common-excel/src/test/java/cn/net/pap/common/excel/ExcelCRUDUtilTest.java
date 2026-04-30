package cn.net.pap.common.excel;

import cn.net.pap.common.excel.dto.PageData;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

public class ExcelCRUDUtilTest {

    @Test
    public void testCRUD() throws Exception {
        String path = Files.createTempFile("crud", ".xlsx").toAbsolutePath().toString();
        String sheet = "Sheet1";

        try {
            ExcelCRUDUtil.createXlsx(path, sheet);

            PageData pd1 = new PageData();
            pd1.put("id", "1");
            pd1.put("name", "alexgaoyh1");
            pd1.put("sex", "male1");
            pd1.put("age", "351");
            ExcelCRUDUtil.insert(pd1, path, sheet);

            PageData pd2 = new PageData();
            pd2.put("id", "2");
            pd2.put("name", "alexgaoyh2");
            pd2.put("sex", "male2");
            pd2.put("age", "352");
            ExcelCRUDUtil.insert(pd2, path, sheet);

            List<PageData> pageData = ExcelCRUDUtil.selectList(path, sheet);

            ExcelCRUDUtil.update(1, 1, "2222", path, sheet);

            ExcelCRUDUtil.delete(0, path, sheet);
        } finally {
            File file = new File(path);
            if (file.exists()) {
                file.delete();
            }
        }

    }

}
