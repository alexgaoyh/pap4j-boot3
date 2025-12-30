package cn.net.pap.example.javafx.dto;

import java.io.Serializable;

/**
 * Exec result
 */
public class ExecResult implements Serializable {

    private final int exitCode;
    private final String stdout;
    private final String stderr;
    private boolean killed;

    public ExecResult(int exitCode, String stdout, String stderr, boolean killed) {
        this.exitCode = exitCode;
        this.stdout = stdout;
        this.stderr = stderr;
        this.killed = killed;
    }

    public int getExitCode() {
        return exitCode;
    }

    public String getStdout() {
        return stdout;
    }

    public String getStderr() {
        return stderr;
    }

    public boolean isSuccess() {
        return exitCode == 0;
    }

    public boolean isKilled() {
        return killed;
    }

    @Override
    public String toString() {
        return "ExecResult{" +
                "exitCode=" + exitCode +
                ", stdout='" + stdout + '\'' +
                ", stderr='" + stderr + '\'' +
                ", killed=" + killed +
                '}';
    }

}
