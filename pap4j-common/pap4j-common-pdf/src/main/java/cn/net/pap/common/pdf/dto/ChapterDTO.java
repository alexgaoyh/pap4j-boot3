package cn.net.pap.common.pdf.dto;

import java.io.Serializable;

public class ChapterDTO implements Serializable {

    String title;

    String content;

    String anchorName;

    public ChapterDTO() {
    }

    public ChapterDTO(String title, String content, String anchorName) {
        this.title = title;
        this.content = content;
        this.anchorName = anchorName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getAnchorName() {
        return anchorName;
    }

    public void setAnchorName(String anchorName) {
        this.anchorName = anchorName;
    }

}
