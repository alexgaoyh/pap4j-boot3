package cn.net.pap.common.file.dto.xml;

import jakarta.xml.bind.annotation.*;

import java.util.List;

@XmlRootElement(name = "page")
@XmlAccessorType(XmlAccessType.FIELD)
public class PageDTO {

    @XmlAttribute(name = "page_id")
    private String pageId;

    @XmlAttribute(name = "relate_id")
    private String relateId;

    @XmlAttribute(name = "width")
    private int width;

    @XmlAttribute(name = "height")
    private int height;

    @XmlAttribute(name = "page_middle_area")
    private String pageMiddleArea;

    @XmlElement(name = "text_region_list")
    private TextRegionList textRegionList;

    @XmlElement(name = "page_text_list")
    private List<String> pageTextList;

    @XmlElement(name = "image_list")
    private ImageList imageList;

    @XmlElement(name = "line_list")
    private List<String> lineList;

    @XmlElement(name = "rectangle_list")
    private List<String> rectangleList;

    @XmlElement(name = "reverse_list")
    private ReverseList reverseList;

    public String getPageId() {
        return pageId;
    }

    public void setPageId(String pageId) {
        this.pageId = pageId;
    }

    public String getRelateId() {
        return relateId;
    }

    public void setRelateId(String relateId) {
        this.relateId = relateId;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public String getPageMiddleArea() {
        return pageMiddleArea;
    }

    public void setPageMiddleArea(String pageMiddleArea) {
        this.pageMiddleArea = pageMiddleArea;
    }

    public TextRegionList getTextRegionList() {
        return textRegionList;
    }

    public void setTextRegionList(TextRegionList textRegionList) {
        this.textRegionList = textRegionList;
    }

    public List<String> getPageTextList() {
        return pageTextList;
    }

    public void setPageTextList(List<String> pageTextList) {
        this.pageTextList = pageTextList;
    }

    public ImageList getImageList() {
        return imageList;
    }

    public void setImageList(ImageList imageList) {
        this.imageList = imageList;
    }

    public List<String> getLineList() {
        return lineList;
    }

    public void setLineList(List<String> lineList) {
        this.lineList = lineList;
    }

    public List<String> getRectangleList() {
        return rectangleList;
    }

    public void setRectangleList(List<String> rectangleList) {
        this.rectangleList = rectangleList;
    }

    public ReverseList getReverseList() {
        return reverseList;
    }

    public void setReverseList(ReverseList reverseList) {
        this.reverseList = reverseList;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class TextRegionList {

        @XmlElement(name = "text_region")
        private List<TextRegion> textRegionList;

        public List<TextRegion> getTextRegionList() {
            return textRegionList;
        }

        public void setTextRegionList(List<TextRegion> textRegionList) {
            this.textRegionList = textRegionList;
        }

        @XmlAccessorType(XmlAccessType.FIELD)
        public static class TextRegion {

            @XmlAttribute(name = "region")
            private String region;

            @XmlElement(name = "text_line")
            private List<TextLine> textLineList;

            public String getRegion() {
                return region;
            }

            public void setRegion(String region) {
                this.region = region;
            }

            public List<TextLine> getTextLineList() {
                return textLineList;
            }

            public void setTextLineList(List<TextLine> textLineList) {
                this.textLineList = textLineList;
            }

            @XmlAccessorType(XmlAccessType.FIELD)
            public static class TextLine {

                @XmlAttribute(name = "region")
                private String region;

                @XmlAttribute(name = "columnNo")
                private int columnNo;

                @XmlAttribute(name = "direction")
                private int direction;

                @XmlAttribute(name = "bussinessType")
                private int bussinessType;

                @XmlAttribute(name = "font_id")
                private int fontId;

                @XmlAttribute(name = "decoration")
                private int decoration;

                @XmlAttribute(name = "rotation")
                private int rotation;

                @XmlElement(name = "text")
                private String text;

                public String getRegion() {
                    return region;
                }

                public void setRegion(String region) {
                    this.region = region;
                }

                public int getColumnNo() {
                    return columnNo;
                }

                public void setColumnNo(int columnNo) {
                    this.columnNo = columnNo;
                }

                public int getDirection() {
                    return direction;
                }

                public void setDirection(int direction) {
                    this.direction = direction;
                }

                public int getBussinessType() {
                    return bussinessType;
                }

                public void setBussinessType(int bussinessType) {
                    this.bussinessType = bussinessType;
                }

                public int getFontId() {
                    return fontId;
                }

                public void setFontId(int fontId) {
                    this.fontId = fontId;
                }

                public int getDecoration() {
                    return decoration;
                }

                public void setDecoration(int decoration) {
                    this.decoration = decoration;
                }

                public int getRotation() {
                    return rotation;
                }

                public void setRotation(int rotation) {
                    this.rotation = rotation;
                }

                public String getText() {
                    return text;
                }

                public void setText(String text) {
                    this.text = text;
                }
            }
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class ImageList {

        @XmlElement(name = "image")
        private List<Image> image;

        public List<Image> getImage() {
            return image;
        }

        public void setImage(List<Image> image) {
            this.image = image;
        }

        @XmlAccessorType(XmlAccessType.FIELD)
        public static class Image {

            @XmlAttribute(name = "name")
            private String name;

            @XmlAttribute(name = "region")
            private String region;

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public String getRegion() {
                return region;
            }

            public void setRegion(String region) {
                this.region = region;
            }
        }

    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class ReverseList {

        @XmlElement(name = "reverse")
        private List<Reverse> reverse;

        public List<Reverse> getReverse() {
            return reverse;
        }

        public void setReverse(List<Reverse> reverse) {
            this.reverse = reverse;
        }

        @XmlAccessorType(XmlAccessType.FIELD)
        public static class Reverse {

            @XmlAttribute(name = "type")
            private String type;

            @XmlAttribute(name = "ids")
            private String ids;

            @XmlAttribute(name = "variant")
            private int variant;

            @XmlAttribute(name = "image_name")
            private String imageName;

            @XmlAttribute(name = "region")
            private String region;

            public String getType() {
                return type;
            }

            public void setType(String type) {
                this.type = type;
            }

            public String getIds() {
                return ids;
            }

            public void setIds(String ids) {
                this.ids = ids;
            }

            public int getVariant() {
                return variant;
            }

            public void setVariant(int variant) {
                this.variant = variant;
            }

            public String getImageName() {
                return imageName;
            }

            public void setImageName(String imageName) {
                this.imageName = imageName;
            }

            public String getRegion() {
                return region;
            }

            public void setRegion(String region) {
                this.region = region;
            }
        }
    }
}
