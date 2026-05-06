package cn.net.pap.common.datastructure.myersdiff;

import com.github.difflib.text.DiffRow;
import com.github.difflib.text.DiffRowGenerator;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class JavaDiffUtils415Test {

    private static final Logger log = LoggerFactory.getLogger(JavaDiffUtils415Test.class);

    @Test
    public void test1() {
        String original = "Hello world\nWelcome to CMS";
        String revised = "Hello brave world\nWelcome to CMS system";

        // 生成行级差异
        DiffRowGenerator generator = DiffRowGenerator.create()
                .showInlineDiffs(true)
                .ignoreWhiteSpaces(true)
                .build();
        List<DiffRow> diffRows = generator.generateDiffRows(
                List.of(original.split("\n")),
                List.of(revised.split("\n"))
        );

        // 输出 HTML 格式的差异
        StringBuilder htmlOutput = new StringBuilder("");
        for (DiffRow row : diffRows) {
            htmlOutput.append(row.getOldLine()).append("\n");
            htmlOutput.append(row.getNewLine()).append("\n");
        }

        log.info("{}", htmlOutput);
    }

}
