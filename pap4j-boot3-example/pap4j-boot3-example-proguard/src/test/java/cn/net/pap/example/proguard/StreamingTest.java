package cn.net.pap.example.proguard;

import cn.net.pap.example.proguard.entity.Proguard;
import cn.net.pap.example.proguard.service.IProguardService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {cn.net.pap.example.proguard.Pap4jBoot3ExampleProguardApplication.class})
public class StreamingTest {

    @Autowired
    private IProguardService proguardService;

    @Autowired
    private EntityManager entityManager;

    // @Test
    public void testStream() throws InterruptedException {
        init();

        try {
            Session session = entityManager.unwrap(Session.class);
            Transaction transaction = session.beginTransaction();

            session.doWork(connection -> {
                try (
                        PreparedStatement preparedStatement = connection.prepareStatement(
                                "UPDATE proguard SET proguard_idx = ? WHERE proguard_id = ?"
                        );
                        ScrollableResults scrollableResults = session.createQuery(
                                        "FROM Proguard WHERE proguardName = :proguardName", Proguard.class
                                )
                                .setParameter("proguardName", "alexgaoyh")
                                .setFetchSize(50)
                                .scroll(ScrollMode.FORWARD_ONLY)
                ) {
                    int count = 0;
                    int batchSize = 50;

                    while (scrollableResults.next()) {
                        Proguard entity = (Proguard) scrollableResults.get();
                        preparedStatement.setInt(1, 9999);
                        preparedStatement.setLong(2, entity.getProguardId());
                        preparedStatement.addBatch();

                        if (++count % batchSize == 0) {
                            preparedStatement.executeBatch();
                        }
                    }
                    if (count % batchSize != 0) {
                        preparedStatement.executeBatch(); // 处理剩余数据
                    }
                }
            });

            transaction.commit(); // 统一提交事务
        } catch (Exception e) {
            try {
                entityManager.unwrap(Session.class).getTransaction().rollback();
            } catch (Exception rollbackEx) {
                rollbackEx.printStackTrace();
            }
            throw new RuntimeException("Error during batch update", e);
        }

        List<Proguard> all = proguardService.findAll();
        System.out.println(all);
    }

    private void init() {
        for(int idx = 0; idx < 99; idx++) {
            Proguard proguard1 = geneEntity();
            proguard1.setProguardId(Long.parseLong(idx + ""));
            proguardService.saveAndFlush(proguard1);
        }
    }

    private Proguard geneEntity() {
        Map<String, Object> extMap = new HashMap<>();
        extMap.put("timeswap", System.currentTimeMillis());
        List<String> extList = new ArrayList<>();
        extList.add("A");

        Proguard proguard = new Proguard();
        proguard.setProguardName("alexgaoyh");
        proguard.setExtMap(extMap);
        proguard.setExtList(extList);

        Map<String, Object> abstractMap = new HashMap<>();
        abstractMap.put("extMap", extMap);
        abstractMap.put("extList", extList);

        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arrayNode = mapper.createArrayNode();
        JsonNode nestedObject = mapper.valueToTree(abstractMap);
        arrayNode.add(nestedObject);
        ObjectNode objectNode = mapper.valueToTree(abstractMap);
        proguard.setAbstractObj(objectNode);
        proguard.setAbstractList(arrayNode);
        return proguard;
    }

}
