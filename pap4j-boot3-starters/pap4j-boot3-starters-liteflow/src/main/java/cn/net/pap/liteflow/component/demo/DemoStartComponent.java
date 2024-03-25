package cn.net.pap.liteflow.component.demo;

import cn.net.pap.liteflow.component.demo.dto.DemoContextDTO;
import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeComponent;

@LiteflowComponent(id = "demoStart", name = "demoStart")
public class DemoStartComponent extends NodeComponent {
    @Override
    public void process() throws Exception {
        DemoContextDTO demoContextDTO = (DemoContextDTO) this.getFirstContextBean();
        demoContextDTO.getContextMap().put("demoStart", demoContextDTO.getContextMap().get("BUSS_ID") + "-demoStart");

    }
}
