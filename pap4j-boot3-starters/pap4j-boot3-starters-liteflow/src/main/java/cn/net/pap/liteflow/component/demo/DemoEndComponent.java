package cn.net.pap.liteflow.component.demo;

import cn.net.pap.liteflow.component.demo.dto.DemoContextDTO;
import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeComponent;

@LiteflowComponent(id = "demoEnd", name = "demoEnd")
public class DemoEndComponent extends NodeComponent {
    @Override
    public void process() throws Exception {
        DemoContextDTO demoContextDTO = (DemoContextDTO) this.getFirstContextBean();
        demoContextDTO.getContextMap().put("demoEnd", demoContextDTO.getContextMap().get("demoProcess") + ".1");
    }

}
