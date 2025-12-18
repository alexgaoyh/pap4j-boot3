package cn.net.pap.example.apitester.dto;

import java.io.Serializable;

/**
 * 对象合并
 */
public class MergeRequest implements Serializable {

    private String fileName;

    private int totalChunks;

    private String uploadId;

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(int totalChunks) {
        this.totalChunks = totalChunks;
    }

    public String getUploadId() {
        return uploadId;
    }

    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

}
