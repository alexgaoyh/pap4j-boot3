package cn.net.pap.common.datastructure.cpu;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;

/**
 * <h1>CPU 核心信息工具类 (Cpu Info Utility)</h1>
 * <p>提供探测操作系统与当前进程 CPU 负载的核心实用方法。</p>
 * <p>使用了反射机制获取 {@code com.sun.management.OperatingSystemMXBean} 的实例方法进行负载检测，从而兼容不同的 JDK 实现。</p>
 *
 * @author alexgaoyh
 */
public class CpuInfoUtil {

    /**
     * <p>日志记录器，用于记录获取信息时的异常情况。</p>
     */
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

    /**
     * <p>获取当前线程关联或者预估分配的 CPU 核心编号。</p>
     * <p>该方法会通过探测当前 JVM 进程的 CPU 负载指标尝试计算。如果当前环境不支持 {@code com.sun.management.OperatingSystemMXBean}，则返回 {@code -1}。</p>
     *
     * @return 当前预估绑定的 CPU 核心数索引；若获取失败或不支持则返回 {@code -1}
     */
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
