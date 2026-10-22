package cn.net.pap.common.core.dto;

import java.io.Serializable;
import java.nio.channels.FileChannel;

/**
 * 文件分段信息传输对象
 * <p>
 * 用于多线程分段读取或内存映射（MMAP）分块处理，记录分块起始位、结束位与文件通道句柄。
 * </p>
 */
public class FileSegmentDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private long start;
    private long end;
    private FileChannel fileChannel;

    public FileSegmentDTO() {
    }

    /**
     * 构建文件分段对象。
     *
     * @param start       起始字节位置
     * @param end         结束字节位置
     * @param fileChannel 文件通道句柄
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
