package cn.net.pap.example.actuator.admin;

import org.junit.Test;
import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.util.FormatUtil;

public class OshiJunit {

    @Test
    public void test1() {
        SystemInfo systemInfo = new SystemInfo();
        HardwareAbstractionLayer hardware = systemInfo.getHardware();

        System.out.println("=== 简单IO监控 ===");

        hardware.getDiskStores().forEach(disk -> {
            System.out.println("磁盘: " + disk.getName());
            System.out.println("  读取: " + FormatUtil.formatBytes(disk.getReadBytes()));
            System.out.println("  写入: " + FormatUtil.formatBytes(disk.getWriteBytes()));
            System.out.println("  读取操作: " + disk.getReads());
            System.out.println("  写入操作: " + disk.getWrites());
            System.out.println("  当前队列长度: " + disk.getCurrentQueueLength());
        });

    }

}
