package cn.net.pap.example.doris;

import cn.net.pap.example.doris.entity.Doris;
import cn.net.pap.example.doris.mapper.DorisMapper;
import cn.net.pap.example.doris.service.DorisService;
import cn.net.pap.example.doris.service.IDorisTransServiceInMysqlDB;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestConstructor;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;

@SpringBootTest
@TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
public class DorisMapperTest {

    private final DorisMapper dorisMapper;
    private final DorisService dorisService;
    private final IDorisTransServiceInMysqlDB dorisTransServiceInMysqlDB;

    public DorisMapperTest(DorisMapper dorisMapper, DorisService dorisService, @Qualifier("dorisTransServiceInMysqlDB") IDorisTransServiceInMysqlDB dorisTransServiceInMysqlDB) {
        this.dorisMapper = dorisMapper;
        this.dorisService = dorisService;
        this.dorisTransServiceInMysqlDB = dorisTransServiceInMysqlDB;
    }

    @BeforeAll
    public static void checkMysqlAvailable() {
        boolean isUp = false;
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("192.168.1.115", 9030), 1000);
            isUp = true;
        } catch (Exception e) {
            // connection failed
        }
        Assumptions.assumeTrue(isUp, "doris is not running on 192.168.1.115:9030. Skipping tests.");
    }

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
        dorisTransServiceInMysqlDB.updateTestNoExceptionInMysqlDB();
        dorisTransServiceInMysqlDB.updateTestNoExceptionInMysqlDB();
    }

    // @Test
    @Order(6)
    public void test6UpdateTestTestInMysqlDB1() {
        dorisTransServiceInMysqlDB.updateTestTestInMysqlDB();
    }

    // @Test
    @Order(7)
    public void test6UpdateTestTestInMysqlDB2() {
        dorisService.updateTestTestInMysqlDB();
    }

    // @Test
    @Order(8)
    public void test7UpdateTestTestInMysqlDB2() {
        dorisService.updateTestTestInMysqlDB2();
    }

}
