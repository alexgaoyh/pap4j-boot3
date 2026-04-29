package cn.net.pap.common.opencv;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PinsFaceRecognitionVectorTest {
    private static final Logger log = LoggerFactory.getLogger(PinsFaceRecognitionVectorTest.class);

    /**
     * 人脸数据集\Pins Face Recognition\105_classes_pins_dataset
     * https://www.kaggle.com/datasets/hereisburak/pins-face-recognition?resource=download
     *
     * 移除  Pedro Alonso180_2138.jpg
     */
    @Test
    public void classes_pins_dataset() throws Exception {
        String basePath = "";
        try {
            basePath = Files.createTempDirectory("classes_pins_dataset").toAbsolutePath().toString();
            String busPath = basePath + File.separator + "Pins Face Recognition\\105_classes_pins_dataset";
            if(!new File(busPath).isDirectory()) {
                return;
            }
            Stream<Path> topDirStream = Files.list(Paths.get(busPath));
            List<Path> topDirList = topDirStream.collect(Collectors.toList());
            for(Path path : topDirList) {

                List<Map<String, Object>> vectorMapList = new ArrayList<>();

                Stream<Path> picStream = Files.list(Paths.get(path.toUri()));
                List<Path> picPathList = picStream.collect(Collectors.toList());
                for(Path picPath : picPathList) {
                    log.info("{}", picPath);
                    float[] floats = OpenCVUtils.matOfKeyPointImage2(picPath.toString());

                    Map<String, Object> vectorMap = new HashMap<>();
                    String picName = picPath.toString().substring(picPath.toString().lastIndexOf(File.separator) + 1, picPath.toString().length());
                    vectorMap.put("picName", picName);
                    vectorMap.put("vector", floats);

                    vectorMapList.add(vectorMap);
                }

                String dirName = path.toString().substring(path.toString().lastIndexOf(File.separator) + 1, path.toString().length());

                ObjectMapper objectMapper = new ObjectMapper();
                try (FileWriter file = new FileWriter(basePath + File.separator + "vector" + File.separator +  dirName + ".json")) {
                    file.write(objectMapper.writeValueAsString(vectorMapList));
                    file.flush();
                }
                catch (IOException e) {
                }

            }
        } finally {
            Files.walkFileTree(Paths.get(basePath), new SimpleFileVisitor<Path>() {
                // 先删除文件
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                // 再删除文件夹（此时文件夹已为空）
                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }

    }
}
