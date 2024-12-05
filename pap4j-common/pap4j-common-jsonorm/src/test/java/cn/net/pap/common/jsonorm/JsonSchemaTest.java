package cn.net.pap.common.jsonorm;

import cn.net.pap.common.jsonorm.dto.MappingORMDTO;
import cn.net.pap.common.jsonorm.util.JsonSchemaUtil;
import org.junit.jupiter.api.Test;

public class JsonSchemaTest {

    @Test
    public void test1() throws Exception {
        System.out.println(JsonSchemaUtil.toSchema(MappingORMDTO.class));
    }

}
