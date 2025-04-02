package cn.net.pap.example.proguard;

import cn.net.pap.example.proguard.entity.AutoIncrePreKey;
import cn.net.pap.example.proguard.repository.AutoIncrePreKeyRepository;
import cn.net.pap.example.proguard.service.IAutoIncrePreKeyService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {cn.net.pap.example.proguard.Pap4jBoot3ExampleProguardApplication.class})
public class AutoIncrePreKeyTest {

    @Autowired
    private IAutoIncrePreKeyService autoIncrePreKeyService;

    @Autowired
    private AutoIncrePreKeyRepository autoIncrePreKeyRepository;

    @Test
    public void preKeyTest() throws Exception {
        AutoIncrePreKey autoIncrePreKey1 = new AutoIncrePreKey();
        autoIncrePreKey1.setName("1");

        AutoIncrePreKey autoIncrePreKey2 = new AutoIncrePreKey();

        AutoIncrePreKey autoIncrePreKey3 = new AutoIncrePreKey();
        autoIncrePreKey3.setName("3");

        autoIncrePreKeyService.saveAndFlush(autoIncrePreKey1);

        try {
            autoIncrePreKeyService.saveAndFlush(autoIncrePreKey2);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        autoIncrePreKeyService.saveAndFlush(autoIncrePreKey3);

        List<AutoIncrePreKey> all = autoIncrePreKeyService.findAll();
        for(AutoIncrePreKey autoIncrePreKey : all){
            System.out.println(autoIncrePreKey.toString());
        }

    }

}
