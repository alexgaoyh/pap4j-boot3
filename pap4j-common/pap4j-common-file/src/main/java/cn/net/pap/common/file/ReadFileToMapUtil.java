package cn.net.pap.common.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

/**
 * 从文件中读取行信息并存储到 map 中，
 */
public class ReadFileToMapUtil {

    private static final Logger log = LoggerFactory.getLogger(ReadFileToMapUtil.class);

    /**
     * 从文本中，按行读取，每行使用 separator 进行分割，存储数据到 ConcurrentHashMap 中返回.
     *
     * @param filePath
     * @param separator
     * @return
     */
    public static ConcurrentHashMap<String, String> toMap(String filePath, byte separator) {
        ConcurrentHashMap<String, String> lineMap = new ConcurrentHashMap<>();

        try (RandomAccessFile file = new RandomAccessFile(filePath, "r");
             FileChannel channel = file.getChannel()) {

            long fileSize = channel.size();
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize);

            ForkJoinPool pool = new ForkJoinPool();
            pool.invoke(new ParseTask(buffer, 0, (int) fileSize, separator, lineMap));

        } catch (IOException e) {
            log.error("toMap", e);
        }

        return lineMap;
    }

    /**
     * 解析任务
     */
    static class ParseTask extends RecursiveAction {
        private static final int THRESHOLD = 8192; // 可以根据性能测试调整阈值大小
        private final MappedByteBuffer buffer;
        private final int start;
        private final int end;
        private final byte separator;
        private final ConcurrentHashMap<String, String> lineMap;

        ParseTask(MappedByteBuffer buffer, int start, int end, byte separator, ConcurrentHashMap<String, String> lineMap) {
            this.buffer = buffer;
            this.start = start;
            this.end = end;
            this.separator = separator;
            this.lineMap = lineMap;
        }

        @Override
        protected void compute() {
            if ((end - start) <= THRESHOLD) {
                processChunk();
            } else {
                int mid = (start + end) >>> 1;

                // 确保在行边界分割块
                while (mid < end && buffer.get(mid) != '\n') {
                    mid++;
                }

                invokeAll(
                        new ParseTask(buffer, start, mid, separator, lineMap),
                        new ParseTask(buffer, mid + 1, end, separator, lineMap)
                );
            }
        }

        private void processChunk() {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            boolean withinLine = false;

            for (int i = start; i < end; i++) {
                byte b = buffer.get(i);
                if (b == '\n') {
                    processLine(byteArrayOutputStream);
                    byteArrayOutputStream.reset();
                    withinLine = false;
                } else {
                    byteArrayOutputStream.write(b);
                    withinLine = true;
                }
            }

            if (withinLine) {
                // Process the last line in the chunk
                processLine(byteArrayOutputStream);
            }
        }

        private void processLine(ByteArrayOutputStream byteArrayOutputStream) {
            byte[] lineBytes = byteArrayOutputStream.toByteArray();
            int separatorIndex = findByte(lineBytes, separator);
            if (separatorIndex != -1) {
                String key = stringAt(lineBytes, 0, separatorIndex);
                String value = stringAt(lineBytes, separatorIndex + 1, lineBytes.length);
                lineMap.put(key, value);
            } else {
                String key = stringAt(lineBytes, 0, lineBytes.length);
                lineMap.put(key.trim(), "");
            }
        }

        private int findByte(byte[] array, byte target) {
            for (int i = 0; i < array.length; i++) {
                if (array[i] == target) {
                    return i;
                }
            }
            return -1;
        }

        private String stringAt(byte[] array, int start, int end) {
            return new String(array, start, end - start, StandardCharsets.UTF_8);
        }
    }

    /**
     * 读取文本内容到 map。 相较于前面方法，执行时间较长。
     *
     * @param filePath
     * @param separator
     * @return
     */
    @Deprecated
    public static ConcurrentHashMap<String, String> toMap1(String filePath, byte separator) {
        ConcurrentHashMap<String, String> lineMap = new ConcurrentHashMap<>();
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(filePath))) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            StringBuilder line = new StringBuilder();

            while ((bytesRead = bis.read(buffer)) != -1) {
                for (int i = 0; i < bytesRead; i++) {
                    if (buffer[i] == '\n') {
                        byte[] lineBytes = line.toString().getBytes(StandardCharsets.UTF_8);
                        int semicolonIndex = findByte(lineBytes, separator);
                        if (semicolonIndex != -1) {
                            String key = stringAt(lineBytes, 0, semicolonIndex);
                            String value = stringAt(lineBytes, semicolonIndex + 1, lineBytes.length);
                            lineMap.put(key, value);
                        } else {
                            String key = stringAt(lineBytes, 0, lineBytes.length);
                            lineMap.put(key, "");
                        }
                        line.setLength(0);
                    } else {
                        line.append((char) buffer[i]);
                    }
                }
            }
            if (line.length() > 0) {
                byte[] lineBytes = line.toString().getBytes(StandardCharsets.UTF_8);
                int semicolonIndex = findByte(lineBytes, separator);
                if (semicolonIndex != -1) {
                    String key = stringAt(lineBytes, 0, semicolonIndex);
                    String value = stringAt(lineBytes, semicolonIndex + 1, lineBytes.length);
                    lineMap.put(key, value);
                } else {
                    String key = stringAt(lineBytes, 0, lineBytes.length);
                    lineMap.put(key, "");
                }
            }
        } catch (IOException e) {
            log.error("toMap1", e);
        }

        return lineMap;
    }

    @Deprecated
    public static int findByte(byte[] array, byte target) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == target) {
                return i;
            }
        }
        return -1;
    }

    @Deprecated
    public static String stringAt(byte[] array, int start, int end) {
        return new String(array, start, end - start, StandardCharsets.UTF_8);
    }

}
