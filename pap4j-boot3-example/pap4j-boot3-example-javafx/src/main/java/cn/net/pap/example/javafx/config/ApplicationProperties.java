package cn.net.pap.example.javafx.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Properties;

/**
 * 配置文件
 */
public class ApplicationProperties {

    private static final Properties PROPS = new Properties();

    /**
     * 图标
     */
    public static final javafx.scene.image.Image APP_ICON = new javafx.scene.image.Image(
            Objects.requireNonNull(ApplicationProperties.class.getClassLoader().getResourceAsStream("alexgaoyh.png"))
    );

    static {
        try (InputStream in = ApplicationProperties.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (in == null) {
                throw new RuntimeException("application.properties not found");
            }
            PROPS.load(in);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load config", e);
        }
    }

    public static String get(String key) {
        return PROPS.getProperty(key);
    }

    public static String getImageMagickPath() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            String path = get("win.imagemagickpath");
            if (path.contains("${user.home}")) {
                path = path.replace("${user.home}", System.getProperty("user.home"));
            }
            return path;
        } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix") || osName.contains("mac")) {
            String path = get("linux.imagemagickpath");
            if (path.contains("${user.home}")) {
                path = path.replace("${user.home}", System.getProperty("user.home"));
            }
            return path;
        } else {
            throw new UnsupportedOperationException("Unsupported operating system: " + osName);
        }
    }

    /**
     * 临时文件的目录
     * @return
     */
    public static String getImageTmpFolder() {
        try {
            String tmpStr = getImageMagickPath() + File.separator + "pap4j-boot3-example-javafx";
            Path tmpPath = Paths.get(tmpStr);
            Files.createDirectories(tmpPath);
            return tmpStr;
        } catch (IOException e) {
            throw new RuntimeException("无法创建临时目录", e);
        }
    }

    public static int getInt(String key, int defaultVal) {
        return Integer.parseInt(PROPS.getProperty(key, String.valueOf(defaultVal)));
    }

    public static boolean getBoolean(String key, boolean defaultVal) {
        return Boolean.parseBoolean(PROPS.getProperty(key, String.valueOf(defaultVal)));
    }

}
