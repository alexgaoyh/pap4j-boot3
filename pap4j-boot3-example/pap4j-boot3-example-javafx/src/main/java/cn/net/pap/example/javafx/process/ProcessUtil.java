package cn.net.pap.example.javafx.process;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class ProcessUtil {

    private static final Logger log = LoggerFactory.getLogger(ProcessUtil.class);

    public static boolean magick_imageRemoveIn(String inputPath, String outputPath, double x1, double y1, double x2, double y2) throws IOException {
        Process process = null;
        InputStream inputStream = null;
        InputStream errorStream = null;
        try {
            String drawCommand = String.format("rectangle %.2f,%.2f %.2f,%.2f", x1, y1, x2, y2);

            ProcessBuilder processBuilder = new ProcessBuilder( "magick", inputPath, "-fill", "white", "-draw", drawCommand, outputPath );

            process = processBuilder.start();

            inputStream = process.getInputStream();
            errorStream = process.getErrorStream();

            consumeStream(inputStream);
            consumeStream(errorStream);

            boolean exited = process.waitFor(60, TimeUnit.SECONDS);
            if (!exited) {
                process.destroyForcibly();
                log.warn("Magick convert timed out");
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException e) {
            log.warn("Magick command failed: {}", e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Magick conversion interrupted", e);
            return false;
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
                if (errorStream != null) {
                    errorStream.close();
                }
            } catch (IOException e) {
                log.warn("Error closing process streams", e);
            }
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static void consumeStream(InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            while (reader.readLine() != null) {
            }
        } catch (IOException e) {
            log.debug("Error consuming process stream", e);
        }
    }

}
