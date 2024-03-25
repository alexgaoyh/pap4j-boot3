package cn.net.pap.liteflow;

import cn.net.pap.liteflow.component.demo.dto.DemoContextDTO;
import com.yomahub.liteflow.core.FlowExecutor;
import com.yomahub.liteflow.flow.LiteflowResponse;
import jakarta.annotation.Resource;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {LiteflowApplication.class})
public class LiteflowDemoTest {

    @Resource
    private FlowExecutor flowExecutor;

    @Test
    public void liteflowDemoTest() throws Exception {
        LiteflowResponse response = flowExecutor.execute2Resp("demo", null, DemoContextDTO.class);
        DemoContextDTO context = response.getFirstContextBean();
        System.out.println(context);
    }
}
