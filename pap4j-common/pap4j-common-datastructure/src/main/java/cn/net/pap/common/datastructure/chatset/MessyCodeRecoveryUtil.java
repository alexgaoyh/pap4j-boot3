package cn.net.pap.common.datastructure.chatset;

import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessyCodeRecoveryUtil {

    private static final Pattern CHINESE_PATTERN = Pattern.compile("[\u4E00-\u9FA5]");

    private static final String[] encodings = {
            "UTF-8", "GBK", "ISO-8859-1", "GB2312", "Big5", "UTF-16",
            "ISO-2022-JP", "Shift_JIS", "EUC-JP", "windows-1252"
    };

    public static List<RecoveryDTO> tryDecode(String input) {
        List<RecoveryDTO> decodedResults = new ArrayList<>();

        Set<String> availableCharsetsSet = Charset.availableCharsets().keySet();

        List<String> availableCharsets = Arrays.asList(encodings);

        for (String encodingOut : availableCharsets) {
            for (String encodingIn : availableCharsets) {
                try {
                    byte[] bytes = input.getBytes(Charset.forName(encodingOut));

                    String decodedString = new String(bytes, Charset.forName(encodingIn));

                    if (isValid(decodedString)) {
                        decodedResults.add(new RecoveryDTO(encodingOut, encodingIn, decodedString));
                    }
                } catch (Exception e) {
                }
            }
        }
        return decodedResults;
    }

    private static boolean isValid(String str) {
        return CHINESE_PATTERN.matcher(str).find();
    }

    public static int countChineseCharacters(RecoveryDTO str) {
        Matcher matcher = CHINESE_PATTERN.matcher(str.getText());
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    public static List<RecoveryDTO> sortByChineseCount(List<RecoveryDTO> results) {
        results.sort((s1, s2) -> countChineseCharacters(s2) - countChineseCharacters(s1));
        return results;
    }

    public static class RecoveryDTO implements Serializable {
        private String encode;

        private String decode;

        private String text;

        public RecoveryDTO() {
        }

        public RecoveryDTO(String encode, String decode, String text) {
            this.encode = encode;
            this.decode = decode;
            this.text = text;
        }

        public String getEncode() {
            return encode;
        }

        public void setEncode(String encode) {
            this.encode = encode;
        }

        public String getDecode() {
            return decode;
        }

        public void setDecode(String decode) {
            this.decode = decode;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return "RecoveryDTO{" +
                    "encode='" + encode + '\'' +
                    ", decode='" + decode + '\'' +
                    ", text='" + text + '\'' +
                    '}';
        }
    }

}
