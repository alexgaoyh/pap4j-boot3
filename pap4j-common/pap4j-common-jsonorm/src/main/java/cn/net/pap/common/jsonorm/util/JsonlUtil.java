package cn.net.pap.common.jsonorm.util;

import java.io.*;

/**
 * jsonl 文件读写
 */
public class JsonlUtil {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JsonlUtil.class);

    /**
     * 写入最后一行
     *
     * @param filePath 文件路径
     * @param jsonData 要写入的 JSON 数据
     * @return 写入成功返回 true，失败返回 false
     */
    public static boolean writeLastLine(String filePath, String jsonData) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, true))) {
            writer.write(jsonData);
            writer.newLine();
        } catch (IOException e) {
            log.error("Failed to write last line to file: {}", filePath, e);
            return false;
        }
        return true;
    }

    /**
     * 读最后一行
     *
     * @param filePath 文件路径
     * @return 文件最后一行的内容，文件不存在或为空时返回 null
     */
    public static String readLastLine(String filePath) {
        File file = new File(filePath);
        if (!file.exists() || file.length() == 0) {
            return null;
        }
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long length = file.length();
            long pointer = length - 1;

            long endOfLine = length;   // 最后一行的结束索引 (不含换行符)
            long startOfLine = 0;      // 最后一行的起始索引
            boolean skippingTrailing = true;

            while (pointer >= 0) {
                raf.seek(pointer);
                int c = raf.read();
                if (c == '\n' || c == '\r') {
                    if (skippingTrailing) {
                        endOfLine = pointer; // 缩减右边界，过滤部换行符
                    } else {
                        startOfLine = pointer + 1; // 找到倒数第二个换行符，确定左边界
                        break;
                    }
                } else {
                    skippingTrailing = false; // 遇到第一个非换行字符，停止过滤尾部换行
                }
                pointer--;
            }

            if (startOfLine >= endOfLine) {
                return "";
            }

            raf.seek(startOfLine);
            byte[] bytes = new byte[(int) (endOfLine - startOfLine)];
            raf.readFully(bytes);
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to read last line from file: {}", filePath, e);
            return null;
        }
    }


}
