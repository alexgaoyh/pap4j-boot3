package cn.net.pap.example.proguard;

import cn.net.pap.example.proguard.entity.NumberSegment;
import cn.net.pap.example.proguard.service.impl.NumberSegmentService;
import cn.net.pap.example.proguard.util.NumberSegmentUtil;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestConstructor;

@SpringBootTest(classes = {cn.net.pap.example.proguard.Pap4jBoot3ExampleProguardApplication.class})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
public class NumberSegmentTest {

    private static final Logger log = LoggerFactory.getLogger(NumberSegmentTest.class);

    private final NumberSegmentService numberSegmentService;

    public NumberSegmentTest(NumberSegmentService numberSegmentService) {
        this.numberSegmentService = numberSegmentService;
    }

    @Test
    public void segmentTest() {
        NumberSegment numberSegment = new NumberSegment();
        numberSegment.setName("PAP");
        numberSegment.setSegmentPrefix("PAP-");
        numberSegment.setCurrentValue(0);
        numberSegmentService.save(numberSegment);

        for(int i = 0; i < 13; i++) {
            String nextNumber = NumberSegmentUtil.getNextNumber("PAP");
            log.info("{}", nextNumber);
        }
    }
}
