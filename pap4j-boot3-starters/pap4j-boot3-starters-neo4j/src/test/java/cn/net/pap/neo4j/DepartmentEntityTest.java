package cn.net.pap.neo4j;

import cn.net.pap.neo4j.deserializer.jackson.DepartmentEntityDeserializer;
import cn.net.pap.neo4j.entity.DepartmentEntity;
import cn.net.pap.neo4j.repository.DepartmentRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {Neo4jApplication.class})
public class DepartmentEntityTest {

    @Autowired
    private DepartmentRepository departmentRepository;

    // @Test
    public void initData() throws Exception {
        // 这里的数据可以从 pap4j-common/pap4j-common-excel/cn.net.pap.common.excel.ExcelUtilTest.parentChild() 这里解析出来
        // 整体流程仿照 从 xls 解析数据，然后序列化/反序列化传输数据，然后存储至图数据库.
        String initJson = "[{\"remark\":\"A\",\"child\":[{\"remark\":\"A01\",\"child\":[{\"remark\":\"A0101\",\"child\":[{\"remark\":\"A010101\",\"parent\":{\"remark\":\"A0101\"}},{\"remark\":\"A010102\",\"parent\":{\"remark\":\"A0101\"}},{\"remark\":\"A010103\",\"parent\":{\"remark\":\"A0101\"}},{\"remark\":\"A010104\",\"parent\":{\"remark\":\"A0101\"}}],\"parent\":{\"remark\":\"A01\"}},{\"remark\":\"A0102\",\"child\":[{\"remark\":\"A010201\",\"parent\":{\"remark\":\"A0102\"}},{\"remark\":\"A010202\",\"parent\":{\"remark\":\"A0102\"}},{\"remark\":\"A010203\",\"parent\":{\"remark\":\"A0102\"}},{\"remark\":\"A010204\",\"parent\":{\"remark\":\"A0102\"}}],\"parent\":{\"remark\":\"A01\"}}],\"parent\":{\"remark\":\"A\"}},{\"remark\":\"A02\",\"child\":[{\"remark\":\"A0201\",\"child\":[{\"remark\":\"A020101\",\"parent\":{\"remark\":\"A0201\"}},{\"remark\":\"A020102\",\"parent\":{\"remark\":\"A0201\"}}],\"parent\":{\"remark\":\"A02\"}},{\"remark\":\"A0202\",\"child\":[{\"remark\":\"A020201\",\"parent\":{\"remark\":\"A0202\"}}],\"parent\":{\"remark\":\"A02\"}}],\"parent\":{\"remark\":\"A\"}}]}]";

        ObjectMapper objectMapperDeserializer = new ObjectMapper();
        SimpleModule module2 = new SimpleModule();
        module2.addDeserializer(DepartmentEntity.class, new DepartmentEntityDeserializer());
        objectMapperDeserializer.registerModule(module2);
        List<DepartmentEntity> departmentEntities = objectMapperDeserializer.readValue(initJson, new TypeReference<List<DepartmentEntity>>() {});

        departmentRepository.saveAll(departmentEntities);

    }

}
