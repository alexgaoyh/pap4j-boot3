package org.ofdrw.layout;

import org.ofdrw.core.basicStructure.doc.CT_PageArea;

/**
 * 自定义无 ApplicationBox 的页面布局
 */
public class PapNoAppBoxPageLayout extends PageLayout {

    public PapNoAppBoxPageLayout(Double width, Double height) {
        super(width, height);
    }

    @Override
    public CT_PageArea getPageArea() {
        // 重写此方法：只返回 PhysicalBox，不设置 ApplicationBox
        return new CT_PageArea()
                .setPhysicalBox(0, 0, this.getWidth(), this.getHeight());
    }

    @Override
    public PapNoAppBoxPageLayout clone() {
        PapNoAppBoxPageLayout copy = new PapNoAppBoxPageLayout(this.getWidth(), this.getHeight());
        // 复制父类的边距
        copy.setMargin(this.getMarginTop(), this.getMarginRight(), this.getMarginBottom(), this.getMarginLeft());
        return copy;
    }

}