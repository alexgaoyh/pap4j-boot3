package cn.net.pap.example.proguard;

import cn.net.pap.example.proguard.entity.AutoIncrePreKey;
import cn.net.pap.example.proguard.repository.AutoIncrePreKeyRepository;
import cn.net.pap.example.proguard.service.IAutoIncrePreKeyService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestConstructor;

import java.util.List;

@SpringBootTest(classes = {cn.net.pap.example.proguard.Pap4jBoot3ExampleProguardApplication.class})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
public class AutoIncrePreKeyTest {

    private final IAutoIncrePreKeyService autoIncrePreKeyService;
    private final AutoIncrePreKeyRepository autoIncrePreKeyRepository;

    public AutoIncrePreKeyTest(IAutoIncrePreKeyService autoIncrePreKeyService, AutoIncrePreKeyRepository autoIncrePreKeyRepository) {
        this.autoIncrePreKeyService = autoIncrePreKeyService;
        this.autoIncrePreKeyRepository = autoIncrePreKeyRepository;
    }

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
