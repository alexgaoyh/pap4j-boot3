package cn.net.pap.common.datastructure.charset;

import cn.net.pap.common.datastructure.chatset.MessyCodeRecoveryUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

public class MessyCodeRecoveryUtilTest {

    @Test
    public void recovery() {
        String messyString = "重点建设办公室";

        String[] encodings = {
                "UTF-8", "GBK", "ISO-8859-1", "GB2312", "Big5", "UTF-16",
                "ISO-2022-JP", "Shift_JIS", "EUC-JP", "windows-1252"
        };

        List<MessyCodeRecoveryUtil.RecoveryDTO> decodedResults = MessyCodeRecoveryUtil.tryDecode(messyString);

        // 排序并输出结果
        List<MessyCodeRecoveryUtil.RecoveryDTO> sortedResults = MessyCodeRecoveryUtil.sortByChineseCount(decodedResults);
        for (MessyCodeRecoveryUtil.RecoveryDTO result : sortedResults) {
            System.out.println(result.getText() + " (" + result.toString() + ")");
        }

    }
}
