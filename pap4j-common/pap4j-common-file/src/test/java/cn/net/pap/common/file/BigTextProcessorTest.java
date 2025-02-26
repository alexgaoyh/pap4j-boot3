package cn.net.pap.common.file;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class BigTextProcessorTest {

    // @Test
    public void testBigText() throws IOException {
        try {
            readFile("C:\\Users\\86181\\Desktop\\big.txt", line -> {
                List<String> words = new ArrayList<>();
                words.add(line);
                return words;
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 以流式方式读取大型txt文件，确保不会出现中文乱码。
     *
     * @param filePath      文件路径
     * @param lineProcessor 每一行的处理逻辑
     * @throws IOException 如果读取文件时发生错误
     */
    public static void readFile(String filePath, LineProcessor lineProcessor) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                List<String> words = lineProcessor.process(line);
                System.out.println(words);
            }
        }
    }

    /**
     * 每一行的处理逻辑接口
     */
    public interface LineProcessor {
        List<String> process(String line);
    }

}
