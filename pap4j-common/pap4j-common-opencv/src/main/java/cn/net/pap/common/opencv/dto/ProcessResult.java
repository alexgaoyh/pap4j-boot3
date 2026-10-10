package cn.net.pap.common.opencv.dto;

public class ProcessResult {

    public volatile boolean finished = false;

    public volatile Integer exitCode;

    public volatile String output;

    public ProcessResult() {
    }

    /**
     * 构造处理结果。
     *
     * @param finished 是否完成
     * @param exitCode 退出码
     * @param output   输出内容
     */
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
