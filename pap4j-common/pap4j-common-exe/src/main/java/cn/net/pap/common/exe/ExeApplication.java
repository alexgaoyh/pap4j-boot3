package cn.net.pap.common.exe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 可执行程序主入口.
 * <p>
 * 打包为 exe 后供外部通过 ProcessBuilder 调用.
 * 执行成功退出码为 0，发生异常退出码为 1.
 */
public class ExeApplication {

    private static final Logger log = LoggerFactory.getLogger(ExeApplication.class);

    /**
     * 主函数入口.
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        int exitCode = run(args);
        System.exit(exitCode);
    }

    /**
     * 执行业务逻辑.
     *
     * @param args 命令行参数
     * @return 退出码：0 为成功，非 0 为异常
     */
    public static int run(String[] args) {
        try {
            log.info("[Exe-Main] Application started with {} args.", args != null ? args.length : 0);
            // 待补充的具体业务逻辑

            // 问题：子进程存活且既不输出也不退出，管道写端未关且无数据，导致父进程 read 永久阻塞等不到 EOF。  Thread.sleep(Long.MAX_VALUE);
            // 问题：子进程输出过量撑满内核管道缓冲区（或串行读取 stdout/stderr），反向堵死子进程引发父子互锁。  boolean flag = true; while (flag) { log.info("{}", "A".repeat(4096)); }

            return 0;
        } catch (Exception e) {
            log.error("[Exe-Main] Application encountered exception: ", e);
            return 1;
        }
    }
}
