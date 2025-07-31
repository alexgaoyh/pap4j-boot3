package cn.net.pap.common.jsonorm;

import cn.net.pap.common.jsonorm.dto.JsonDTO;
import cn.net.pap.common.jsonorm.parser.OptimizedJsonParser;
import cn.net.pap.common.jsonorm.util.JsonORMUtil;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.openjdk.jol.info.ClassLayout;
import org.openjdk.jol.info.GraphLayout;

import java.io.File;

public class OptimizedJsonParserTest {

    //@Test
    public void optimizedTest1() throws Exception {
        String json = JsonORMUtil.readFileToString(new File("C:\\Users\\86181\\Desktop\\bigjson.txt"));
        // 这里就是初始化一下
        JsonDTO jsonDTO2 = OptimizedJsonParser.parseWithOptimization(json, JsonDTO.class);

        long l = System.currentTimeMillis();
        JsonDTO jsonDTO = OptimizedJsonParser.parseWithOptimization(json, JsonDTO.class);
        System.out.println(System.currentTimeMillis() - l);

        // 打印对象内部结构（对象头、字段对齐等）
        System.out.println("ClassLayout:");
        System.out.println(ClassLayout.parseInstance(jsonDTO).toPrintable());

        // 打印对象图总大小（包括引用的对象）
        System.out.println("GraphLayout:");
        System.out.println(GraphLayout.parseInstance(jsonDTO).toFootprint());
        System.out.println("Total size: " + GraphLayout.parseInstance(jsonDTO).totalSize() + " bytes");

    }


    //@Test
    public void noOptimizedTest1() throws Exception {
        String json = JsonORMUtil.readFileToString(new File("C:\\Users\\86181\\Desktop\\bigjson.txt"));
        ObjectMapper OBJECT_MAPPER = new ObjectMapper();
        OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        JsonDTO jsonDTO2 = OBJECT_MAPPER.readValue(json, JsonDTO.class);

        long l = System.currentTimeMillis();
        JsonDTO jsonDTO = OBJECT_MAPPER.readValue(json, JsonDTO.class);
        System.out.println(System.currentTimeMillis() - l);

        // 打印对象内部结构（对象头、字段对齐等）
        System.out.println("ClassLayout:");
        System.out.println(ClassLayout.parseInstance(jsonDTO).toPrintable());

        // 打印对象图总大小（包括引用的对象）
        System.out.println("GraphLayout:");
        System.out.println(GraphLayout.parseInstance(jsonDTO).toFootprint());
        System.out.println("Total size: " + GraphLayout.parseInstance(jsonDTO).totalSize() + " bytes");

    }

}
