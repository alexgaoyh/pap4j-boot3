package cn.net.pap.common.file;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

public class ReadXMLFileToStrUtil {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ReadXMLFileToStrUtil.class);

    /**
     * 不同编码下读取 XML
     * @param filePath
     * @return
     * @throws IOException
     * @throws XMLStreamException
     */
    public static String readFileWithEncoding(String filePath) throws IOException, XMLStreamException {
        String encoding = getEncoding(filePath);
        StringBuilder contentBuilder = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(filePath), Charset.forName(encoding)))) {
            String line;
            while ((line = br.readLine()) != null) {
                contentBuilder.append(line);
            }
        }
        return contentBuilder.toString();
    }

    private static String getEncoding(String filePath) throws IOException, XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        XMLStreamReader reader = null;
        try {
            reader = factory.createXMLStreamReader(new FileInputStream(filePath));
            return reader.getCharacterEncodingScheme();
        } catch (Exception e) {
            log.error("读取 XML 文件编码失败", e);
            return null;
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

}
