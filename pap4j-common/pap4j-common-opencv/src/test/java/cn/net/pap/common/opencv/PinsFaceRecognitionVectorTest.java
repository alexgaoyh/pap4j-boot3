package cn.net.pap.common.opencv;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PinsFaceRecognitionVectorTest {

    /**
     * 人脸数据集\Pins Face Recognition\105_classes_pins_dataset
     *
     * 移除  Pedro Alonso180_2138.jpg
     */
    // @Test
    public void classes_pins_dataset() throws Exception {
        List<Map<String, Object>> vectorMapList = new ArrayList<>();

        String basePath = "C:\\Users\\86181\\Desktop";
        Stream<Path> topDirStream = Files.list(Paths.get(basePath + File.separator + "Pins Face Recognition\\105_classes_pins_dataset"));
        List<Path> topDirList = topDirStream.collect(Collectors.toList());
        for(Path path : topDirList) {
            Stream<Path> picStream = Files.list(Paths.get(path.toUri()));
            List<Path> picPathList = picStream.collect(Collectors.toList());
            for(Path picPath : picPathList) {
                System.out.println(picPath);
                float[] floats = OpenCVUtils.matOfKeyPointImage2(picPath.toString());

                Map<String, Object> vectorMap = new HashMap<>();
                String picName = picPath.toString().substring(picPath.toString().lastIndexOf(File.separator), picPath.toString().length());
                vectorMap.put("picName", picName);
                vectorMap.put("vector", floats);

                vectorMapList.add(vectorMap);
            }
        }
        ObjectMapper objectMapper = new ObjectMapper();
        try (FileWriter file = new FileWriter(basePath + File.separator + "105_classes_pins_dataset_vector.json")) {
            file.write(objectMapper.writeValueAsString(vectorMapList));
            file.flush();
        }
        catch (IOException e) {
        }
    }
}
