package cn.net.pap.example.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "异步任务进程执行结果")
public class ProcessResult extends cn.net.pap.common.core.dto.ProcessResult {

    public ProcessResult() {
        super();
    }

    /**
     * 构造进程执行结果。
     *
     * @param finished 是否已执行完成
     * @param exitCode 进程退出码
     * @param output   标准输出
     */
    public ProcessResult(boolean finished, Integer exitCode, String output) {
        super(finished, exitCode, output);
    }

}
