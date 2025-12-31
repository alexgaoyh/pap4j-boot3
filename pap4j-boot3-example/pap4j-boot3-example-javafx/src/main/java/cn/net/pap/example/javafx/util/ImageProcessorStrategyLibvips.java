package cn.net.pap.example.javafx.util;

import cn.net.pap.example.javafx.config.ApplicationProperties;
import cn.net.pap.example.javafx.dto.ExecResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Strategy LibVips
 */
public class ImageProcessorStrategyLibvips implements ImageProcessorStrategy {

    private static final Logger log = LoggerFactory.getLogger(ImageProcessorStrategyLibvips.class);

    // libvips 对应的文件夹根路径，不包含 bin 文件夹
    private static String libvipsPath = null;

    // 操作系统 win linux mac
    private static String osDir = null;

    static {
        try {
            libvipsPath = ApplicationProperties.get("image.processor.type.libvips.path");
            if(null == libvipsPath || "".equals(libvipsPath) || !new File(libvipsPath).exists()) {
                throw new RuntimeException("image.processor.type.libvips.path need set");
            }
            String osName = System.getProperty("os.name").toLowerCase();
            if (osName.contains("win")) {
                osDir = "win";
            } else if (osName.contains("linux")) {
                osDir = "linux";
            } else if (osName.contains("mac")) {
                osDir = "mac";
            } else {
                throw new UnsupportedOperationException("Unsupported OS: " + osName);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    @Override
    public ExecResult imageRemoveIn(String inputPath, String outputPath, double x1, double y1, double x2, double y2) {
        try {
            // 异步启动保存任务
            CompletableFuture<Void> saveTask = CompletableFuture.runAsync(() -> {
                try {
                    ImageProcessorStrategy.imageSaveInTmpFolder(inputPath);
                } catch (IOException e) {
                    throw new CompletionException(e);
                }
            });

            Map<String, String> envHome = new HashMap<>();
            envHome.put("PATH", libvipsPath + File.separator + "bin" + File.pathSeparator + PATH);

            String commandFile = "imageRemoveIn";
            if(osDir.equals("win")) {
                commandFile = commandFile + ".bat";
            } else if(osDir.equals("linux")) {
                commandFile = commandFile + ".sh";
            }
            String cmd = String.join(" ", commandFile, "\"" + inputPath + "\"", x1 + "", y1 + "", x2 - x1 + "", y2 - y1 + "");

            // 等保存任务完成后再执行命令 等待 imageSaveInTmpFolder 完成
            saveTask.join();

            ExecResult execResult = ImageProcessorStrategy.execWithShell(cmd, envHome, new File(libvipsPath + File.separator + "bin"), 60000);

            if (!execResult.isSuccess()) {
                log.error("Magick failed: {}\n{}", cmd, execResult.getStderr());
            }

            return execResult;
        } catch (Exception e) {
            log.warn("Magick command failed: {}", e);
            return new ExecResult(999, "", e.getMessage(), true);
        } finally {

        }
    }

}
