package cn.net.pap.quartz.util;

import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 获取 JVM 当前所有线程的完整运行状态，并附带“可读性极强”的完整堆栈与锁信息，用于线上问题排查。
 */
public class GetThreadsWithFullStackTraceUtil {

    /**
     * 获取 JVM 当前所有线程的运行状态及完整堆栈信息。等价于 “程序内版 jstack”。
     * 会一次性抓取 JVM 中所有线程的快照
     *
     * @return
     */
    public List<Map<String, Object>> getAll() {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] threadInfos = threadMXBean.dumpAllThreads(true, true);

        List<Map<String, Object>> threads = new ArrayList<>();
        for (ThreadInfo info : threadInfos) {
            Map<String, Object> thread = new LinkedHashMap<>();
            thread.put("id", info.getThreadId());
            thread.put("name", info.getThreadName());
            thread.put("state", info.getThreadState().name());
            thread.put("blockedCount", info.getBlockedCount());
            thread.put("waitedCount", info.getWaitedCount());

            // 完整的栈跟踪信息
            StackTraceElement[] stackTrace = info.getStackTrace();
            if (stackTrace.length > 0) {
                List<Map<String, String>> fullStackTrace = new ArrayList<>();
                for (StackTraceElement element : stackTrace) {
                    Map<String, String> stackFrame = new HashMap<>();
                    stackFrame.put("className", element.getClassName());
                    stackFrame.put("methodName", element.getMethodName());
                    stackFrame.put("fileName", element.getFileName());
                    stackFrame.put("lineNumber", element.getLineNumber() > 0 ? String.valueOf(element.getLineNumber()) : "Native Method");
                    stackFrame.put("fullMethod", element.getClassName() + "." + element.getMethodName() + (element.getLineNumber() > 0 ? "(" + element.getFileName() + ":" + element.getLineNumber() + ")" : "(" + element.getFileName() + ")"));
                    fullStackTrace.add(stackFrame);
                }
                thread.put("stackTrace", fullStackTrace);
                thread.put("stackDepth", stackTrace.length);

                // 也可以保存为字符串格式
                thread.put("stackTraceString", formatStackTrace(info));
            } else {
                thread.put("stackTrace", Collections.emptyList());
                thread.put("stackTraceString", "No stack trace available");
            }

            // 锁信息（如果启用）
            if (info.getLockInfo() != null) {
                Map<String, Object> lockInfo = new HashMap<>();
                lockInfo.put("className", info.getLockInfo().getClassName());
                lockInfo.put("identityHashCode", info.getLockInfo().getIdentityHashCode());
                thread.put("lockInfo", lockInfo);
            }

            // 如果是阻塞状态，显示阻塞的锁
            if (info.getThreadState() == Thread.State.BLOCKED && info.getLockInfo() != null) {
                thread.put("blockedOn", info.getLockInfo().toString());
            }

            threads.add(thread);
        }

        return threads;
    }

    private String formatStackTrace(ThreadInfo threadInfo) {
        StringBuilder sb = new StringBuilder();
        sb.append("\"").append(threadInfo.getThreadName()).append("\" ").append("Id=").append(threadInfo.getThreadId()).append(" ").append(threadInfo.getThreadState());

        if (threadInfo.getLockName() != null) {
            sb.append(" on ").append(threadInfo.getLockName());
        }

        if (threadInfo.getLockOwnerName() != null) {
            sb.append(" owned by \"").append(threadInfo.getLockOwnerName()).append("\" Id=").append(threadInfo.getLockOwnerId());
        }

        if (threadInfo.isSuspended()) {
            sb.append(" (suspended)");
        }

        if (threadInfo.isInNative()) {
            sb.append(" (in native)");
        }

        sb.append("\n");

        StackTraceElement[] stackTrace = threadInfo.getStackTrace();
        for (StackTraceElement element : stackTrace) {
            sb.append("\tat ").append(element.toString()).append("\n");
        }

        // 显示被锁住的监视器
        LockInfo[] lockedMonitors = threadInfo.getLockedMonitors();
        for (LockInfo monitor : lockedMonitors) {
            sb.append("\t- locked <").append(monitor.getIdentityHashCode()).append("> (a ").append(monitor.getClassName()).append(")\n");
        }

        // 显示被锁住的同步器
        LockInfo[] lockedSynchronizers = threadInfo.getLockedSynchronizers();
        if (lockedSynchronizers.length > 0) {
            sb.append("\n\tNumber of locked synchronizers = ").append(lockedSynchronizers.length).append("\n");
            for (LockInfo sync : lockedSynchronizers) {
                sb.append("\t- <").append(sync.getIdentityHashCode()).append("> (a ").append(sync.getClassName()).append(")\n");
            }
        }

        return sb.toString();
    }

}
