package cn.net.pap.example.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "异步任务进程执行结果")
public class ProcessResult {

    @Schema(description = "进程是否已执行完成", example = "true")
    public volatile boolean finished = false;

    @Schema(description = "进程退出码 (0表示正常退出)", example = "0")
    public volatile Integer exitCode;

    @Schema(description = "进程标准控制台输出", example = "Process executed successfully.")
    public volatile String output;

    public ProcessResult() {
    }

    public ProcessResult(boolean finished, Integer exitCode, String output) {
        this.finished = finished;
        this.exitCode = exitCode;
        this.output = output;
    }

    public boolean isFinished() {
        return finished;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public String getOutput() {
        return output;
    }

}
