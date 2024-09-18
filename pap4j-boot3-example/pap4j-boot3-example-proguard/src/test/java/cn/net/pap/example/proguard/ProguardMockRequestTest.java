package cn.net.pap.example.proguard;

import cn.net.pap.example.proguard.entity.Proguard;
import cn.net.pap.example.proguard.service.IProguardService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mock Request RequestContextHolder for Service-Bean
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = {cn.net.pap.example.proguard.Pap4jBoot3ExampleProguardApplication.class})
public class ProguardMockRequestTest {

    @Autowired
    IProguardService proguardService;

    @Before
    public void setUp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("pap-tenant-id", "pap.net.cn");
        MockHttpServletResponse response = new MockHttpServletResponse();

        RequestAttributes requestAttributes = new ServletRequestAttributes(request, response);
        RequestContextHolder.setRequestAttributes(requestAttributes, true);
    }

    @Test
    public void mockitoTest() {
        Map<String, Object> extMap = new HashMap<>();
        extMap.put("timeswap", System.currentTimeMillis());
        List<String> extList = new ArrayList<>();
        extList.add("A");

        Map<String, Object> abstractMap = new HashMap<>();
        abstractMap.put("extMap", extMap);
        abstractMap.put("extList", extList);

        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arrayNode = mapper.createArrayNode();
        JsonNode nestedObject = mapper.valueToTree(abstractMap);
        arrayNode.add(nestedObject);

        Proguard proguard1 = new Proguard();
        proguard1.setProguardId(1l);
        proguard1.setProguardName("alexgaoyh");
        proguard1.setExtMap(extMap);
        proguard1.setExtList(extList);
        proguard1.setAbstractList(arrayNode);
        proguardService.saveAndFlush(proguard1);

    }

}
