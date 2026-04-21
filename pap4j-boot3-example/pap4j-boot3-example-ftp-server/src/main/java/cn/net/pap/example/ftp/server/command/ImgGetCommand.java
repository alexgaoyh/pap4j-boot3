package cn.net.pap.example.ftp.server.command;

import org.apache.ftpserver.command.AbstractCommand;
import org.apache.ftpserver.ftplet.DefaultFtpReply;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpRequest;
import org.apache.ftpserver.impl.FtpIoSession;
import org.apache.ftpserver.impl.FtpServerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

/**
 * <p>测试示例：</p>
 * <pre>{@code
 *     @Test
 *     @Order(9)
 *     public void testImgGet() throws IOException {
 *         boolean b = ftpClient.sendSiteCommand("IMGGET demo");
 *         String reply = ftpClient.getReplyString();
 *         if(b) {
 *             String base64Data = reply.substring(4).trim();
 *             byte[] imageBytes = Base64.getDecoder().decode(base64Data);
 *             try (ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes)) {
 *                 BufferedImage image = ImageIO.read(bais);
 *                 System.out.println(image);
 *             }
 *         }
 *     }
 * }</pre>
 */
public class ImgGetCommand extends AbstractCommand {

    private final Logger LOG = LoggerFactory.getLogger(ImgGetCommand.class);

    @Override
    public void execute(FtpIoSession session, FtpServerContext context, FtpRequest request) throws IOException, FtpException {
        try {
            BufferedImage image = createDemoImage();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());

            session.write(new DefaultFtpReply(200, base64));
            LOG.info("SITE IMGGET sent image as Base64, size: {}", base64.length());

        } catch (Exception e) {
            LOG.error("Failed to execute SITE IMGGET", e);
            session.write(new DefaultFtpReply(550, "IMGGET failed"));
        }
    }

    private BufferedImage createDemoImage() {
        BufferedImage img = new BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB);

        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 400, 200);
        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.BOLD, 22));
        g.drawString("IMGGET OK", 120, 110);
        g.dispose();

        return img;
    }
}
