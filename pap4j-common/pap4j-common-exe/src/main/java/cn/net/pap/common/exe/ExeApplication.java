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
            return 0;
        } catch (Exception e) {
            log.error("[Exe-Main] Application encountered exception: ", e);
            return 1;
        }
    }
}
