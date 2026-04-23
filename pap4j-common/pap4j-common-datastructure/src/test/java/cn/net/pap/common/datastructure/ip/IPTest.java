package cn.net.pap.common.datastructure.ip;

import cn.net.pap.common.datastructure.resource.TestResourceUtil;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;

public class IPTest {

    @Test
    public void ipRangeExpander() {
        Path outPath = null;
        try {
            outPath = Files.createTempFile("ip-test-", ".txt");
            expandIPRanges(TestResourceUtil.getFile("chnroute.txt").toPath().toAbsolutePath().toString(), outPath.toAbsolutePath().toString());
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(outPath != null) {
                outPath.toFile().delete();
            }
        }
    }

    /**
     * 将IP段展开为单个IP地址并写入文件
     *
     * @param inputFilePath  输入文件路径，每行一个IP段
     * @param outputFilePath 输出文件路径，用于存储展开后的IP地址
     * @throws IOException 如果读写文件时发生错误
     */
    public static void expandIPRanges(String inputFilePath, String outputFilePath) throws IOException {
        // 读取输入文件并展开IP段
        try (BufferedReader reader = new BufferedReader(new FileReader(inputFilePath));
             BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] ipRanges = line.split("\\s+"); // 假设每行可能有多个IP段，用空格分隔
                for (String ipRange : ipRanges) {
                    if (ipRange.contains("/")) {
                        String[] parts = ipRange.split("/");
                        String baseIp = parts[0];
                        int prefixLength = Integer.parseInt(parts[1]);
                        InetAddress inetAddress = InetAddress.getByName(baseIp);
                        byte[] bytes = inetAddress.getAddress();
                        long startIp = ipToLong(bytes);
                        long endIp = startIp | ((1L << (32 - prefixLength)) - 1);

                        // 直接写入文件，避免内存溢出
                        for (long ip = startIp; ip <= endIp; ip++) {
                            writer.write(longToIp(ip));
                            writer.newLine();
                        }
                    } else {
                        writer.write(ipRange);
                        writer.newLine();
                    }
                }
            }
        }
    }

    // 将IP地址转换为long类型
    private static long ipToLong(byte[] bytes) {
        long result = 0;
        for (byte b : bytes) {
            result = (result << 8) | (b & 0xFF);
        }
        return result;
    }

    // 将long类型的IP地址转换为字符串
    private static String longToIp(long ip) {
        return ((ip >> 24) & 0xFF) + "." +
                ((ip >> 16) & 0xFF) + "." +
                ((ip >> 8) & 0xFF) + "." +
                (ip & 0xFF);
    }


}
