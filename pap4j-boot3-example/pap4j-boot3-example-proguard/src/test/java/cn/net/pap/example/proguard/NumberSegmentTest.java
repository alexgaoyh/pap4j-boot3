package cn.net.pap.example.proguard;

import cn.net.pap.example.proguard.entity.NumberSegment;
import cn.net.pap.example.proguard.service.impl.NumberSegmentService;
import cn.net.pap.example.proguard.util.NumberSegmentUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {cn.net.pap.example.proguard.Pap4jBoot3ExampleProguardApplication.class})
public class NumberSegmentTest {

    @Autowired
    private NumberSegmentService numberSegmentService;

    @Test
    public void segmentTest() {
        NumberSegment numberSegment = new NumberSegment();
        numberSegment.setName("PAP");
        numberSegment.setSegmentPrefix("PAP-");
        numberSegment.setCurrentValue(0);
        numberSegmentService.save(numberSegment);

        for(int i = 0; i < 13; i++) {
            String nextNumber = NumberSegmentUtil.getNextNumber("PAP");
            System.out.println(nextNumber);
        }
    }
}
