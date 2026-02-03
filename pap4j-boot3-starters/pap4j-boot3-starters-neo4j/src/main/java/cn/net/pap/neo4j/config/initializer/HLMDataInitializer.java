package cn.net.pap.neo4j.config.initializer;

import cn.net.pap.neo4j.entity.HLMEntity;
import cn.net.pap.neo4j.repository.HLMRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

@Component
@ConditionalOnExpression("${neo4j.setup.init.data.HLM:true}")
public class HLMDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(HLMDataInitializer.class);

    @Autowired
    private HLMRepository hlmRepository;

    @Override
    public void run(String... args) throws Exception {
        try {
            ClassPathResource classPathResource = new ClassPathResource("HLM.txt");
            try (InputStream inputStream = classPathResource.getInputStream();
                 InputStreamReader isr = new InputStreamReader(inputStream);
                 BufferedReader br = new BufferedReader(isr)){
                String str;
                while ((str = br.readLine()) != null) {
                    String[] spoArray = str.split(",");
                    HLMEntity sNode = new HLMEntity();
                    sNode.setName(spoArray[2]);
                    HLMEntity oNode = new HLMEntity();
                    oNode.setName(spoArray[0]);
                    sNode.addRelationship(spoArray[1], oNode);

                    hlmRepository.save(sNode);
                }
            }
        } catch (IOException e) {
            log.error("run", e);
        }
    }
}
