package cn.net.pap.liteflow.component.demo.dto;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

public class DemoContextDTO implements Serializable {

    private Map<String, String> contextMap = new LinkedHashMap<>();

    public Map<String, String> getContextMap() {
        return contextMap;
    }

    public void setContextMap(Map<String, String> contextMap) {
        this.contextMap = contextMap;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("DemoContextDTO{");
        sb.append("contextMap=").append(contextMap);
        sb.append('}');
        return sb.toString();
    }
}
