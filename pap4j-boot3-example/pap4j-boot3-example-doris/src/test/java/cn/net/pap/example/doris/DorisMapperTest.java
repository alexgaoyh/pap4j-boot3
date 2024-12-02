package cn.net.pap.example.doris;

import cn.net.pap.example.doris.entity.Doris;
import cn.net.pap.example.doris.mapper.DorisMapper;
import cn.net.pap.example.doris.service.DorisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
public class DorisMapperTest {

    @Autowired
    private DorisMapper dorisMapper;

    @Autowired
    private DorisService dorisService;

    @Test
    public void test1TransactionalTest() {
        dorisService.transactionalTest();
    }

    @Test
    public void test2Select() {
        List<Doris> dorisList = dorisMapper.selectList(null);
        if(dorisList != null && dorisList.size() > 0) {
            dorisList.forEach(System.out::println);
        }
    }

}
