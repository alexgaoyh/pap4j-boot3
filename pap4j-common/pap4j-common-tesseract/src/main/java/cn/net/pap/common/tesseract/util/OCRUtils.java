package cn.net.pap.common.tesseract.util;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.leptonica.PIX;
import org.bytedeco.tesseract.ResultIterator;
import org.bytedeco.tesseract.TessBaseAPI;
import org.bytedeco.tesseract.global.tesseract;

import java.awt.Rectangle;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.bytedeco.leptonica.global.leptonica.pixRead;
import static org.bytedeco.leptonica.global.leptonica.pixDestroy;
import static org.bytedeco.leptonica.global.leptonica.pixGetWidth;
import static org.bytedeco.leptonica.global.leptonica.pixGetHeight;

/**
 * OCR 工具类 - 基于 Tesseract Platform 5.5.1-1.5.12
 */
public class OCRUtils {

    static {
        // 预加载本地库
        Loader.load(org.bytedeco.tesseract.global.tesseract.class);
    }

    private OCRUtils() {
        // 工具类，禁止实例化
    }

    /**
     * OCR 结果对象
     */
    public static class OCRResult {
        private String text;
        private Rectangle boundingBox;
        private float confidence;
        private String level;

        public OCRResult(String text, Rectangle boundingBox, float confidence, String level) {
            this.text = text;
            this.boundingBox = boundingBox;
            this.confidence = confidence;
            this.level = level;
        }

        // Getters
        public String getText() {
            return text;
        }

        public Rectangle getBoundingBox() {
            return boundingBox;
        }

        public float getConfidence() {
            return confidence;
        }

        public String getLevel() {
            return level;
        }

        @Override
        public String toString() {
            return String.format("文本: '%s', 置信度: %.2f%%, 级别: %s, 坐标: [x=%d, y=%d, w=%d, h=%d]",
                    text, confidence, level, boundingBox.x, boundingBox.y,
                    boundingBox.width, boundingBox.height);
        }
    }

    /**
     * 执行 OCR 识别并返回带坐标的结果
     *
     * @param imagePath 图像文件路径
     * @param language  语言代码 (如: "eng", "chi_sim", "eng+chi_sim")
     * @return 带坐标的 OCR 结果列表
     * @throws OCRException 如果 OCR 处理失败
     */
    public static List<OCRResult> recognizeWithCoordinates(String tessdataPath, String imagePath, String language) throws OCRException {
        return recognizeWithCoordinates(tessdataPath, imagePath, language,
                org.bytedeco.tesseract.global.tesseract.PSM_AUTO);
    }

    /**
     * 执行 OCR 识别并返回带坐标的结果（高级配置）
     *
     * @param imagePath   图像文件路径
     * @param language    语言代码
     * @param pageSegMode 页面分割模式
     * @return 带坐标的 OCR 结果列表
     * @throws OCRException 如果 OCR 处理失败
     */
    public static List<OCRResult> recognizeWithCoordinates(String tessdataPath, String imagePath, String language,
                                                           int pageSegMode) throws OCRException {

        List<OCRResult> results = new ArrayList<>();
        TessBaseAPI api = new TessBaseAPI();
        PIX image = null;

        try {
            // 验证文件存在
            validateImageFile(imagePath);

            // 初始化 Tesseract
            initTesseract(api, tessdataPath, language);

            // 设置页面分割模式
            api.SetPageSegMode(pageSegMode);

            // 设置识别器变量以获取更多信息
            api.SetVariable("save_blob_choices", "T");
            api.SetVariable("tessedit_write_unlv", "1");

            // 读取图像
            image = pixRead(imagePath);
            if (image == null) {
                throw new OCRException("无法读取图像文件: " + imagePath);
            }

            api.SetImage(image);

            // 执行识别
            if (api.Recognize(null) != 0) {
                throw new OCRException("OCR 识别失败");
            }

            // 获取完整文本和其坐标
            processResultsAtDifferentLevels(api, image, results);

        } catch (Exception e) {
            if (e instanceof OCRException) {
                throw (OCRException) e;
            }
            throw new OCRException("OCR 处理异常: " + e.getMessage(), e);
        } finally {
            // 清理资源
            cleanUp(api, image);
        }

        return results;
    }

    /**
     * 验证图像文件是否存在
     */
    private static void validateImageFile(String imagePath) throws OCRException {
        File file = new File(imagePath);
        if (!file.exists() || !file.isFile()) {
            throw new OCRException("图像文件不存在: " + imagePath);
        }
    }

    /**
     * 初始化 Tesseract 引擎
     */
    private static void initTesseract(TessBaseAPI api, String tessdataPath, String language) throws OCRException {
        if (api.Init(tessdataPath, language) != 0) {
            throw new OCRException("无法初始化 Tesseract，请检查语言包: " + language);
        }
    }

    /**
     * 清理资源
     */
    private static void cleanUp(TessBaseAPI api, PIX image) {
        if (api != null) {
            api.End();
            api.close();
        }
        if (image != null) {
            pixDestroy(image);
        }
    }

    /**
     * 在不同级别处理结果
     */
    private static void processResultsAtDifferentLevels(TessBaseAPI api, PIX image, List<OCRResult> results) {
        // 获取完整文本
        BytePointer outText = api.GetUTF8Text();
        if (outText != null) {
            try {
                String fullText = outText.getString();
                if (fullText != null && !fullText.trim().isEmpty()) {
                    // 获取图像的宽度和高度
                    int width = pixGetWidth(image);
                    int height = pixGetHeight(image);
                    Rectangle fullBbox = new Rectangle(0, 0, width, height);

                    float pageConf = api.MeanTextConf();
                    results.add(new OCRResult(fullText, fullBbox, pageConf, "PAGE"));
                }
            } finally {
                outText.deallocate();
            }
        }

        // 获取行级别的结果
        processComponentLevelResults(api, results);
    }

    /**
     * 处理行级别结果
     */
    public static void processComponentLevelResults(TessBaseAPI api, List<OCRResult> results) {
        // 获取结果迭代器
        ResultIterator ri = api.GetIterator();
        if (ri == null) {
            return;
        }

        try {
            do {
                BytePointer textPtr = ri.GetUTF8Text(tesseract.RIL_TEXTLINE);
                if (textPtr != null) {
                    String lineText = textPtr.getString();
                    textPtr.deallocate();

                    if (lineText != null && !lineText.trim().isEmpty()) {
                        float confidence = ri.Confidence(tesseract.RIL_TEXTLINE);

                        int[] x1 = new int[1], y1 = new int[1], x2 = new int[1], y2 = new int[1];
                        ri.BoundingBox(tesseract.RIL_TEXTLINE, x1, y1, x2, y2);

                        results.add(new OCRResult(
                                lineText,
                                new Rectangle(x1[0], y1[0], x2[0] - x1[0], y2[0] - y1[0]),
                                confidence,
                                "LINE"
                        ));
                    }
                }
            } while (ri.Next(tesseract.RIL_TEXTLINE));
        } finally {
            ri.close();
        }
    }

    /**
     * 获取字级别的 OCR 结果
     *
     * @param tessdataPath Tesseract tessdata 路径
     * @param imagePath 图像文件路径
     * @param language 语言代码 (如: "eng", "chi_sim", "eng+chi_sim")
     * @return 包含字级别 OCR 结果的列表
     * @throws OCRException 如果 OCR 处理失败
     */
    public static List<OCRResult> recognizeWithWordCoordinates(String tessdataPath, String imagePath, String language) throws OCRException {
        List<OCRResult> results = new ArrayList<>();
        TessBaseAPI api = new TessBaseAPI();
        PIX image = null;

        try {
            // 初始化 Tesseract
            if (api.Init(tessdataPath, language) != 0) {
                throw new OCRException("无法初始化 Tesseract，请检查语言包: " + language);
            }

            // 读取图像文件
            image = pixRead(imagePath);
            if (image == null) {
                throw new OCRException("无法读取图像文件: " + imagePath);
            }

            api.SetImage(image);

            api.Recognize(null);

            // 获取页面迭代器
            ResultIterator iterator = api.GetIterator();
            if (iterator == null) {
                throw new OCRException("无法获取页面迭代器");
            }

            // 遍历字级别的 OCR 结果
            iterator.Begin();
            do {
                String word = iterator.GetUTF8Text(3).getString();
                if (word != null && !word.trim().isEmpty()) {
                    // 获取该字的边界框
                    Rectangle wordBoundingBox = getWordBoundingBox(iterator);

                    // 获取该字的置信度
                    float wordConfidence = getWordConfidence(iterator);

                    // 创建 OCRResult 对象并添加到结果列表
                    results.add(new OCRResult(word, wordBoundingBox, wordConfidence, "WORD"));
                }
            } while (iterator.Next(3)); // 继续迭代下一个字

        } catch (Exception e) {
            if (e instanceof OCRException) {
                throw (OCRException) e;
            }
            throw new OCRException("OCR 处理异常: " + e.getMessage(), e);
        } finally {
            // 清理资源
            cleanUp(api, image);
        }

        return results;
    }

    /**
     * 获取每个字的边界框
     *
     * @param iterator 页迭代器
     * @return 单词的矩形边界框
     */
    private static Rectangle getWordBoundingBox(ResultIterator iterator) {
        int[] x1 = new int[1], y1 = new int[1], x2 = new int[1], y2 = new int[1];
        iterator.BoundingBox(1, x1, y1, x2, y2); // 1 代表字级别
        // 返回该单词的边界框
        return new Rectangle(x1[0], y1[0], x2[0] - x1[0], y2[0] - y1[0]);
    }

    /**
     * 获取每个字的置信度
     *
     * @param iterator 页迭代器
     * @return 单词的置信度
     */
    private static float getWordConfidence(ResultIterator iterator) {
        return iterator.Confidence(1); // 1 代表字级别
    }


    /**
     * OCR 异常类
     */
    public static class OCRException extends Exception {
        public OCRException(String message) {
            super(message);
        }

        public OCRException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
