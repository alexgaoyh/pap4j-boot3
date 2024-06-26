package cn.net.pap.common.jsonorm.util;

import java.io.*;

/**
 * jsonl 文件读写
 */
public class JsonlUtil {

    /**
     * 写入最后一行
     *
     * @param filePath
     * @param jsonData
     * @return
     */
    public static boolean writeLastLine(String filePath, String jsonData) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, true))) {
            writer.write(jsonData);
            writer.newLine();
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    /**
     * 读最后一行
     *
     * @param filePath
     * @return
     */
    public static String readLastLine(String filePath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            String lastLine = null;
            while ((line = reader.readLine()) != null) {
                lastLine = line;
            }
            if (lastLine != null) {
                return lastLine;
            } else {
                return null;
            }
        } catch (IOException e) {
            return null;
        }
    }


}
