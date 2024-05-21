package cn.net.pap.neo4j.config.initializer;

import cn.net.pap.neo4j.entity.HLMEntity;
import cn.net.pap.neo4j.repository.HLMRepository;
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

    @Autowired
    private HLMRepository hlmRepository;

    @Override
    public void run(String... args) throws Exception {
        InputStreamReader isr = null;
        BufferedReader br = null;
        try {
            ClassPathResource classPathResource = new ClassPathResource("HLM.txt");
            InputStream inputStream = classPathResource.getInputStream();
            isr = new InputStreamReader(inputStream);
            br = new BufferedReader(isr);
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
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
                if (isr != null) {
                    isr.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
