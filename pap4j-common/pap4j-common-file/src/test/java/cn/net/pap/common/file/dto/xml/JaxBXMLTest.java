package cn.net.pap.common.file.dto.xml;

import cn.net.pap.common.file.TestResourceUtil;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class JaxBXMLTest {

    @Test
    public void test() {
        try {
            String xmlString = Files.readString(Paths.get(TestResourceUtil.getFile("PageDTO.xml").getAbsolutePath().toString()), StandardCharsets.UTF_8);
            if (xmlString.startsWith("\uFEFF")) {
                xmlString = xmlString.substring(1);
            }
            JAXBContext jaxbContext = JAXBContext.newInstance(PageDTO.class);
            Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
            PageDTO pageDTO = (PageDTO) jaxbUnmarshaller.unmarshal(new StringReader(xmlString));
            System.out.println(pageDTO);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
