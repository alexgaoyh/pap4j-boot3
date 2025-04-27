package cn.net.pap.common.datastructure.cpu;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

public class CpuInfoUtil {

    public static int getCurrentCpuCore() {
        OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
            return ((com.sun.management.OperatingSystemMXBean) osBean).getProcessCpuLoad() >= 0 ?
                    Thread.currentThread().hashCode() % Runtime.getRuntime().availableProcessors() : -1;
        }
        return -1;
    }

}
