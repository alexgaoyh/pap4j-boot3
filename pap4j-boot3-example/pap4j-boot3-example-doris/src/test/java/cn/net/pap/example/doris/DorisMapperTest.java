package cn.net.pap.example.doris;

import cn.net.pap.example.doris.entity.Doris;
import cn.net.pap.example.doris.mapper.DorisMapper;
import cn.net.pap.example.doris.service.DorisService;
import cn.net.pap.example.doris.service.IDorisTransServiceInMysqlDB;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
@TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
public class DorisMapperTest {

    @Autowired
    private DorisMapper dorisMapper;

    @Autowired
    private DorisService dorisService;

    @Resource(name = "dorisTransServiceInMysqlDB")
    private IDorisTransServiceInMysqlDB dorisTransServiceInMysqlDB;

    @Test
    @Order(1)
    public void test1TransactionalTest() {
        dorisService.transactionalTest();
    }

    @Test
    @Order(2)
    public void test2PartFieldUpdateTest() {
        dorisService.partFieldUpdateTest();
    }

    @Test
    @Order(3)
    public void test3Select() {
        LambdaQueryWrapper<Doris> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(Doris::getId);
        List<Doris> dorisList = dorisMapper.selectList(wrapper);
        if(dorisList != null && dorisList.size() > 0) {
            dorisList.forEach(System.out::println);
        }
    }

    // @Test
    @Order(4)
    public void test4UpdateInMysqlDBMulti() {
        dorisTransServiceInMysqlDB.updateTestThrowExceptionInMysqlDB();
    }

    // @Test
    @Order(5)
    public void test5UpdateInMysqlDBMulti() {
        dorisTransServiceInMysqlDB.updateTestNoExceptionInMysqlDB();
    }

}
