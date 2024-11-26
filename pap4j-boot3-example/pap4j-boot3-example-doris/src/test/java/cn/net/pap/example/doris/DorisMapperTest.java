package cn.net.pap.example.doris;

import cn.net.pap.example.doris.entity.Doris;
import cn.net.pap.example.doris.mapper.DorisMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
public class DorisMapperTest {

    @Autowired
    private DorisMapper dorisMapper;

    // @Test
    public void testSelect() {
        List<Doris> dorisList = dorisMapper.selectList(null);
    }

}
