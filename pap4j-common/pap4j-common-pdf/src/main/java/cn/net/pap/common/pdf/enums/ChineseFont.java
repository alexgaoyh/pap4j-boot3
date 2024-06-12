package cn.net.pap.common.pdf.enums;

/**
 * 中文字体
 */
public enum ChineseFont {

    //仿宋体
    SIMFANG("仿宋", "SimFang", "SIMFANG.ttf", "fonts/simfang.ttf"),
    //黑体
    SIMHEI("黑体", "SimHei", "SIMHEI.ttf", "fonts/simhei.ttf"),
    //楷体
    SIMKAI("楷体", "SimKai", "SIMKAI.ttf", "fonts/simkai.ttf"),
    //宋体&新宋体
    SIMSUM("宋体", "SimSun", "simsun.ttc", "fonts/simsun.ttf");

    /**
     * fontName
     */
    private String fontName;

    /**
     * fontAlias
     */
    private String fontAlias;

    /**
     * fontFileName
     */
    private String fontFileName;

    /**
     * location
     */
    private String location;

    /**
     * 获得位置
     * @param fontName
     * @return
     */
    public static String getLocation(String fontName) {
        for(ChineseFont chineseFont : ChineseFont.values()){
            if(chineseFont.getFontName().equals(fontName)) {
                return chineseFont.getLocation();
            }
        }
        return null;
    }

    /**
     * 构造函数
     * @param fontName
     * @param fontAlias
     * @param fontFileName
     * @param location
     */
    private ChineseFont(String fontName, String fontAlias, String fontFileName, String location) {
        this.fontName = fontName;
        this.fontAlias = fontAlias;
        this.fontFileName = fontFileName;
        this.location = location;
    }

    public String getFontName() {
        return fontName;
    }

    public String getFontAlias() {
        return fontAlias;
    }

    public String getFontFileName() {
        return fontFileName;
    }

    public String getLocation() {
        return location;
    }
}
