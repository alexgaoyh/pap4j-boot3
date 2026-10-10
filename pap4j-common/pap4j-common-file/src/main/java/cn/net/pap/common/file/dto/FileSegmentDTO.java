package cn.net.pap.common.file.dto;

import java.io.Serializable;
import java.nio.channels.FileChannel;

public class FileSegmentDTO implements Serializable {

    private long start;

    private long end;

    private FileChannel fileChannel;

    public FileSegmentDTO() {
    }

    /**
     * 构造文件分段。
     *
     * @param start       起始位置
     * @param end         结束位置
     * @param fileChannel 文件通道
     */
    public FileSegmentDTO(long start, long end, FileChannel fileChannel) {
        this.start = start;
        this.end = end;
        this.fileChannel = fileChannel;
    }

    public long getStart() {
        return start;
    }

    public void setStart(long start) {
        this.start = start;
    }

    public long getEnd() {
        return end;
    }

    public void setEnd(long end) {
        this.end = end;
    }

    public FileChannel getFileChannel() {
        return fileChannel;
    }

    public void setFileChannel(FileChannel fileChannel) {
        this.fileChannel = fileChannel;
    }
}
