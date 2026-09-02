package cn.net.pap.common.test;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class AgentTestExecutionListener implements TestExecutionListener {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AgentTestExecutionListener.class);

    private static final Object lock = new Object();

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        // 在新一轮测试开始前，清空旧的 test_failures.md
        try {
            File rootDir = findProjectRoot();
            if (rootDir != null) {
                File diagnosticsDir = new File(rootDir, ".ai/diagnostics");
                if (diagnosticsDir.exists()) {
                    File failureFile = new File(diagnosticsDir, "test_failures.md");
                    if (failureFile.exists()) {
                        failureFile.delete();
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to process test diagnostics", e);
        }
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        // 只记录失败的叶子节点（具体测试方法）
        if (testIdentifier.isTest() && testExecutionResult.getStatus() == TestExecutionResult.Status.FAILED) {
            synchronized (lock) {
                writeFailure(testIdentifier, testExecutionResult.getThrowable());
            }
        }
    }

    private void writeFailure(TestIdentifier testIdentifier, Optional<Throwable> throwableOpt) {
        try {
            File rootDir = findProjectRoot();
            if (rootDir == null) return;

            File diagnosticsDir = new File(rootDir, ".ai/diagnostics");
            if (!diagnosticsDir.exists()) {
                diagnosticsDir.mkdirs();
            }
            File failureFile = new File(diagnosticsDir, "test_failures.md");

            try (PrintWriter out = new PrintWriter(new FileWriter(failureFile, true))) {
                out.println("### ❌ Test Failed: " + testIdentifier.getDisplayName());
                out.println("- **Location**: `" + testIdentifier.getLegacyReportingName() + "`");
                out.println("- **Time**: `" + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "`");
                if (throwableOpt.isPresent()) {
                    Throwable ex = throwableOpt.get();
                    out.println("- **Exception**: `" + ex.getClass().getName() + ": " + ex.getMessage() + "`");
                    out.println("- **Stack Trace Snippet**:");
                    out.println("```text");
                    StackTraceElement[] trace = ex.getStackTrace();
                    int limit = Math.min(trace.length, 8); // 限制在 8 行以内，精炼输出
                    for (int i = 0; i < limit; i++) {
                        out.println("    at " + trace[i]);
                    }
                    if (trace.length > limit) {
                        out.println("    ... " + (trace.length - limit) + " more");
                    }
                    out.println("```");
                }
                out.println("---");
            }
        } catch (Exception e) {
            log.error("Failed to process test diagnostics", e);
        }
    }

    private File findProjectRoot() {
        File currentDir = new File(".").getAbsoluteFile();
        while (currentDir != null) {
            if (new File(currentDir, ".ai").isDirectory() || new File(currentDir, ".agent").isDirectory()) {
                return currentDir;
            }
            currentDir = currentDir.getParentFile();
        }
        return null;
    }
}
