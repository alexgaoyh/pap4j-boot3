package cn.net.pap.liteflow;

import cn.net.pap.liteflow.component.demo.dto.DemoContextDTO;
import com.yomahub.liteflow.core.FlowExecutor;
import com.yomahub.liteflow.flow.LiteflowResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestConstructor;

@SpringBootTest(classes = {LiteflowApplication.class})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
public class LiteflowDemoTest {

    private final FlowExecutor flowExecutor;

    public LiteflowDemoTest(FlowExecutor flowExecutor) {
        this.flowExecutor = flowExecutor;
    }

    // @Test
    public void liteflowDemoTest() throws Exception {
        DemoContextDTO demoContextDTO = new DemoContextDTO();
        demoContextDTO.getContextMap().put("BUSS_ID", System.currentTimeMillis() + "");
        LiteflowResponse response = flowExecutor.execute2Resp("demo", null, demoContextDTO);
        DemoContextDTO context = response.getFirstContextBean();
        System.out.println(context);
    }
}
