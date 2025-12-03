package cn.net.pap.common.file.xml;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public class StaxXmlUtil {

    /**
     * 获取指定节点下的所有子节点 XML 文本
     *
     * @param xmlText  XML 文本
     * @param nodeName 节点名，例如 "personal"
     * @return List，每个元素是一个子节点的 XML 文本
     */
    public static List<String> readChildrenXmlByStax(String xmlText, String nodeName) {
        List<String> result = new ArrayList<>();
        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xmlText));

            StringBuilder sb = null;
            int depth = 0; // 用于记录嵌套层级

            while (reader.hasNext()) {
                int event = reader.next();

                if (event == XMLStreamConstants.START_ELEMENT && nodeName.equals(reader.getLocalName())) {
                    // 找到目标节点
                    sb = new StringBuilder(1024 * 1024); // 预分配 1MB
                    depth = 1;
                    sb.append("<").append(nodeName);
                    for (int i = 0; i < reader.getAttributeCount(); i++) {
                        sb.append(" ").append(reader.getAttributeLocalName(i)).append("=\"").append(reader.getAttributeValue(i)).append("\"");
                    }
                    sb.append(">");
                } else if (sb != null) {
                    switch (event) {
                        case XMLStreamConstants.START_ELEMENT:
                            depth++;
                            sb.append("<").append(reader.getLocalName());
                            for (int i = 0; i < reader.getAttributeCount(); i++) {
                                sb.append(" ").append(reader.getAttributeLocalName(i)).append("=\"").append(reader.getAttributeValue(i)).append("\"");
                            }
                            sb.append(">");
                            break;
                        case XMLStreamConstants.CHARACTERS:
                        case XMLStreamConstants.CDATA: // 支持 CDATA
                            sb.append(reader.getText());
                            break;
                        case XMLStreamConstants.END_ELEMENT:
                            sb.append("</").append(reader.getLocalName()).append(">");
                            depth--;
                            if (depth == 0) {
                                // 一个完整节点读取完成
                                result.add(sb.toString());
                                sb = null;
                            }
                            break;
                        default:
                            break;
                    }
                }
            }

            reader.close();
        } catch (Exception e) {
            throw new RuntimeException("StAX 解析失败: " + e.getMessage(), e);
        }
        return result;
    }


    /**
     * 获取某节点文本内容
     */
    public static String readNodeValueByStax(String xmlText, String nodeName) {
        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xmlText));

            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT && nodeName.equals(reader.getLocalName())) {
                    // 读取文本
                    String text = "";
                    while (reader.hasNext()) {
                        event = reader.next();
                        if (event == XMLStreamConstants.CHARACTERS) {
                            text += reader.getText();
                        } else if (event == XMLStreamConstants.END_ELEMENT && nodeName.equals(reader.getLocalName())) {
                            reader.close();
                            return text.trim();
                        }
                    }
                }
            }

            reader.close();
            return null;
        } catch (Exception e) {
            throw new RuntimeException("StAX 解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * @param xmlText
     * @param parentNodeName
     * @return
     */
    public static String readChildrenXmlValueByStax(String xmlText, String parentNodeName) {
        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xmlText));

            StringBuilder sb = null;
            int depth = 0;
            boolean insideParent = false;

            while (reader.hasNext()) {
                int event = reader.next();

                if (event == XMLStreamConstants.START_ELEMENT && parentNodeName.equals(reader.getLocalName())) {
                    // 进入父节点
                    insideParent = true;
                    depth = 0; // 子节点深度
                    sb = new StringBuilder();
                } else if (insideParent) {
                    if (event == XMLStreamConstants.START_ELEMENT) {
                        depth++;
                        sb.append("<").append(reader.getLocalName());
                        for (int i = 0; i < reader.getAttributeCount(); i++) {
                            sb.append(" ").append(reader.getAttributeLocalName(i)).append("=\"").append(reader.getAttributeValue(i)).append("\"");
                        }
                        sb.append(">");
                    } else if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
                        sb.append(reader.getText());
                    } else if (event == XMLStreamConstants.END_ELEMENT) {
                        if (depth > 0) {
                            sb.append("</").append(reader.getLocalName()).append(">");
                            depth--;
                        } else if (parentNodeName.equals(reader.getLocalName())) {
                            // 父节点结束
                            reader.close();
                            return sb.toString();
                        }
                    }
                }
            }

            reader.close();
            return null;
        } catch (Exception e) {
            throw new RuntimeException("StAX 解析失败: " + e.getMessage(), e);
        }
    }


    /**
     * 统计节点数量
     */
    public static int countNodesByStax(String xmlText, String nodeName) {
        int count = 0;
        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xmlText));

            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT && nodeName.equals(reader.getLocalName())) {
                    count++;
                }
            }

            reader.close();
            return count;
        } catch (Exception e) {
            throw new RuntimeException("StAX 解析失败: " + e.getMessage(), e);
        }
    }
}
