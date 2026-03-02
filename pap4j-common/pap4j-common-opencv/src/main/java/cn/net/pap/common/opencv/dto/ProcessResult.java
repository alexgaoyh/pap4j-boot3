package cn.net.pap.common.opencv.dto;

public class ProcessResult {

    public volatile boolean finished = false;

    public volatile Integer exitCode;

    public volatile String output;

    public ProcessResult() {
    }

    public ProcessResult(Integer exitCode, String output) {
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
