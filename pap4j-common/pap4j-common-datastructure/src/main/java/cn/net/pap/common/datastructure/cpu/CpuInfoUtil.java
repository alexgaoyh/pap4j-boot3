package cn.net.pap.common.datastructure.cpu;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;

public class CpuInfoUtil {

    private static final Logger log = LoggerFactory.getLogger(CpuInfoUtil.class);

    private static final Class<?> SUN_OS_BEAN_CLASS;
    private static final Method GET_PROCESS_CPU_LOAD_METHOD;
    private static final boolean SUN_OPERATING_SYSTEM_MX_BEAN_AVAILABLE;

    static {
        Class<?> sunOsBeanClass = null;
        Method getProcessCpuLoadMethod = null;
        boolean available = false;

        try {
            sunOsBeanClass = Class.forName("com.sun.management.OperatingSystemMXBean");
            getProcessCpuLoadMethod = sunOsBeanClass.getMethod("getProcessCpuLoad");
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            if (sunOsBeanClass.isInstance(osBean)) {
                available = true;
            }
        } catch (Exception e) {
            sunOsBeanClass = null;
            getProcessCpuLoadMethod = null;
        }
        SUN_OS_BEAN_CLASS = sunOsBeanClass;
        GET_PROCESS_CPU_LOAD_METHOD = getProcessCpuLoadMethod;
        SUN_OPERATING_SYSTEM_MX_BEAN_AVAILABLE = available;
    }

    public static int getCurrentCpuCore() {
        if (!SUN_OPERATING_SYSTEM_MX_BEAN_AVAILABLE) {
            return -1;
        }

        try {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            Double cpuLoad = (Double) GET_PROCESS_CPU_LOAD_METHOD.invoke(osBean);

            if (cpuLoad != null && cpuLoad >= 0) {
                long threadId = Thread.currentThread().getId();
                return (int) (threadId % Runtime.getRuntime().availableProcessors());
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        }

        return -1;
    }

}
