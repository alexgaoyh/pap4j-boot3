package cn.net.pap.common.jsonorm.tree;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.net.pap.common.jsonorm.util.JacksonUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LinuxTreeToJsonUtilTest {
    private static final Logger log = LoggerFactory.getLogger(LinuxTreeToJsonUtilTest.class);

    // @Test
    public void test1() {
        try {
            String filenameOut = System.getProperty("user.home") + File.separator + "Desktop" + File.separator + "tree.out";
            String filenameJson = System.getProperty("user.home") + File.separator + "Desktop" + File.separator + "tree.json";
            LinuxTreeToJsonUtil.TreeNode root = LinuxTreeToJsonUtil.parseTreeFile(filenameOut);
            if (root != null) {
                // 读取 tree.out， 封装为 json ，写入文件
                ObjectNode json = LinuxTreeToJsonUtil.convertToJson(root);
                ObjectMapper mapper = new ObjectMapper();
                String jsonString = mapper.writeValueAsString(json);
                try (FileWriter writer = new FileWriter(filenameJson)) {
                    writer.write(jsonString);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                // 读取 json 到 List<Map>。 为了后续做数据处理
                JacksonUtil.parseLargeJsonInBatches(filenameJson, batch -> {
                    log.info("{}", "Processing batch with " + batch.size() + " items");
                });


            } else {
                log.info("Failed to parse tree file.");
            }
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
        }
    }

    /**
     * 读取 tree 命令的返回值，然后打印出来每一个文件夹下，jpg文件的数量。
     * @throws IOException
     */
    // @Test
    public void test2() throws IOException {
        Map<Integer, String> dirMap = new LinkedHashMap<>();
        Integer fileCountInt = 0;
        String filename = System.getProperty("user.home") + File.separator + "Desktop" + File.separator + "tree.out";
        List<String> lines = Files.readAllLines(Path.of(filename), StandardCharsets.UTF_8);
        for (Integer idx = 1; idx < lines.size(); idx++) {
            String line = lines.get(idx);
            int levelIdx = line.split("|").length;
            int splitIdx = line.indexOf("+--- ");
            String dirOrFileName = line.substring(splitIdx + "+--- ".length());
            if(dirOrFileName.contains(".jpg")) {
                fileCountInt++;
            } else {
                if(fileCountInt > 0) {
                    log.info("{}", String.join("/", dirMap.values()) + " : " + fileCountInt);
                }
                dirMap.put(levelIdx, dirOrFileName);
                fileCountInt = 0;
            }
        }
    }

}
