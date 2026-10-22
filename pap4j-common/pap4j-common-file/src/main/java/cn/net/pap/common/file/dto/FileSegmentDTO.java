package cn.net.pap.common.file.dto;

import java.nio.channels.FileChannel;

/**
 * 文件分段传输对象
 *
 * @deprecated 请直接使用 {@link cn.net.pap.common.core.dto.FileSegmentDTO}
 */
@Deprecated
public class FileSegmentDTO extends cn.net.pap.common.core.dto.FileSegmentDTO {

    private static final long serialVersionUID = 1L;

    public FileSegmentDTO() {
        super();
    }

    /**
     * 文件分段构造。
     *
     * @param start       起始位置
     * @param end         结束位置
     * @param fileChannel 文件通道
     */
    public FileSegmentDTO(long start, long end, FileChannel fileChannel) {
        super(start, end, fileChannel);
    }
}
