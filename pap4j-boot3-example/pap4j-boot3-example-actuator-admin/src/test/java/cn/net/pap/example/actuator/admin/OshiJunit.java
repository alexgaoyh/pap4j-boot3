package cn.net.pap.example.actuator.admin;

import org.junit.Test;
import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.software.os.OperatingSystem;
import oshi.util.FormatUtil;

import java.util.Optional;

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

    @Test
    public void getMachineCodeTest() {
        SystemInfo si = new SystemInfo();
        HardwareAbstractionLayer hal = si.getHardware();

        // 获取 CPU 序列号
        String processorId = hal.getProcessor().getProcessorIdentifier().getProcessorID();

        // 硬件 UUID
        String hardwareUUID = hal.getComputerSystem().getHardwareUUID();

        // 获取主板序列号
        String baseboardSerialNumber = hal.getComputerSystem().getBaseboard().getSerialNumber();

        // 操作系统级别的唯一 ID，不要版本号！
        OperatingSystem os = si.getOperatingSystem();
        String osId = os.getManufacturer() + os.getFamily();

        // 5. 网卡 MAC 地址。 注意：只取第一个有效的物理网卡 MAC
        String mac = "";
        Optional<NetworkIF> networkIFOptional = hal.getNetworkIFs().stream()
                .filter(net -> !net.isKnownVmMacAddr() && net.getMacaddr().length() > 0)
                .findFirst();
        if(networkIFOptional.isPresent()) {
            mac = networkIFOptional.get().getMacaddr();
        }

        System.out.println(mac);

    }

    private boolean isValid(String s) {
        if (s == null || s.isEmpty()) return false;
        String val = s.toLowerCase();
        // 过滤掉所有云环境常见的无效占位符
        return !val.contains("none") &&
                !val.contains("unknown") &&
                !val.contains("default") &&
                !val.contains("to be filled");
    }

}
