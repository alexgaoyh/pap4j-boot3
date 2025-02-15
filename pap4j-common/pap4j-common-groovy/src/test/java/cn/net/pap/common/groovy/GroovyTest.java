package cn.net.pap.common.groovy;

import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class GroovyTest {

    /**
     *         <dependency>
     *             <groupId>org.codehaus.groovy</groupId>
     *             <artifactId>groovy</artifactId>
     *             <version>3.0.23</version>
     *         </dependency>
     *
     *         @PostMapping("groovy")
     * @param groovy
     * @param method
     * @throws Exception
     */
    public void controllerMethod(String groovy, String method) throws Exception {
        GroovyShell groovyShell = new GroovyShell();
        //装载解析脚本代码
        Script script = groovyShell.parse(groovy);
        //执行
        script.invokeMethod(method, null);
    }

    /**
     * package cn.net.pap.common.groovy
     *
     * import javax.imageio.ImageIO
     * import javax.imageio.ImageReader
     *
     * def imageio(){
     *     for (String mime : ImageIO.getReaderMIMETypes()) {
     *         String spiClass = "";
     *         Iterator<ImageReader> imageReadersByMIMEType = ImageIO.getImageReadersByMIMEType(mime);
     *         while (imageReadersByMIMEType.hasNext()) {
     *             ImageReader spi = imageReadersByMIMEType.next();
     *             spiClass = spiClass + spi.getClass().getName() + " ; ";
     *         }
     *         System.out.println(mime + " : " + spiClass);
     *     }
     * }
     * @throws IOException
     */
    @Test
    public void imageIOTest() throws IOException {
        GroovyShell groovyShell = new GroovyShell();
        //装载解析脚本代码
        Script script = groovyShell.parse(
"package cn.net.pap.common.groovy\n" +
        "\n" +
        "import javax.imageio.ImageIO\n" +
        "import javax.imageio.ImageReader\n" +
        "\n" +
        "def imageio(){\n" +
        "    for (String mime : ImageIO.getReaderMIMETypes()) {\n" +
        "        String spiClass = \"\";\n" +
        "        Iterator<ImageReader> imageReadersByMIMEType = ImageIO.getImageReadersByMIMEType(mime);\n" +
        "        while (imageReadersByMIMEType.hasNext()) {\n" +
        "            ImageReader spi = imageReadersByMIMEType.next();\n" +
        "            spiClass = spiClass + spi.getClass().getName() + \" ; \";\n" +
        "        }\n" +
        "        System.out.println(mime + \" : \" + spiClass);\n" +
        "    }\n" +
        "}"
        );
        //执行
        script.invokeMethod("imageio", null);
    }

    @Test
    public void returnTest() throws IOException {
        GroovyShell groovyShell = new GroovyShell();
        Script script = groovyShell.parse(
    "package cn.net.pap.common.groovy\n" +
            "\n" +
            "def returnStr() {\n" +
            "    return \"alexgaoyh\";\n" +
            "}\n"
        );
        //执行
        Object returnObj = script.invokeMethod("returnStr", null);
        System.out.println(returnObj.toString());
    }

}
