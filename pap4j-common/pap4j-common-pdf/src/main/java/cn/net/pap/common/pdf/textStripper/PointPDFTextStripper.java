package cn.net.pap.common.pdf.textStripper;

import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.List;

/**
 * 自定义解析，返回文字坐标
 */
public class PointPDFTextStripper extends PDFTextStripper {

    public PointPDFTextStripper() {
        super();
    }

    @Override
    protected void writeString(String string, List<TextPosition> textPositions) throws IOException {
        StringBuilder withPointString = new StringBuilder();
        for (TextPosition text : textPositions) {
            withPointString.append(text.getUnicode())
                    .append("[")
                    .append(text.getXDirAdj()).append(",").append(text.getYDirAdj()).append(",").append(text.getEndX()).append(",").append(text.getEndY())
                    .append("]")
                    .append("{").append(text.getFontSize()).append("}")
                    .append("<").append(text.getPageWidth()).append(",").append(text.getPageHeight()).append(">")
                    .append("\n");
        }
        super.writeString(withPointString.toString().substring(0, withPointString.toString().length() - 1), textPositions);
    }

}
