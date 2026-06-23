package cn.net.pap.example.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "ImageIO 接口", description = "提供 ImageIO 支持格式查询")
public class ImageIOController {

    private static final Logger log = LoggerFactory.getLogger(ImageIOController.class);

    @Operation(summary = "获取支持的图片格式", description = "获取系统内 ImageIO 支持的所有图片读取格式名称和对应的 MIME 类型服务类。")
    @GetMapping("imageio")
    public Object imageio() {
        try {
            List<String> formatList = new ArrayList<>();
            for (String format : ImageIO.getReaderFormatNames()) {
                formatList.add(format);
            }
            List<String> mimeList = new ArrayList<>();
            List<String> nptList = new ArrayList<>();
            for (String mime : ImageIO.getReaderMIMETypes()) {
                String spiClass = "";
                Iterator<ImageReader> imageReadersByMIMEType = ImageIO.getImageReadersByMIMEType(mime);
                if(imageReadersByMIMEType != null) {
                    while (imageReadersByMIMEType.hasNext()) {
                        ImageReader spi = imageReadersByMIMEType.next();
                        log.info("ImageReader SPI Class: {}", spi.getClass().getName());
                        spiClass = spiClass + spi.getClass().getName() + " ; ";
                    }
                    mimeList.add(mime + " : " + spiClass);
                } else {
                    mimeList.add(mime + " : " + spiClass);
                    nptList.add(mime);
                }

            }

            Map<String, Object> map = new HashMap<>();
            map.put("formatList", formatList);
            map.put("mimeList", mimeList);
            map.put("nptList", nptList);
            return map;
        } catch (Exception e) {
            log.error("ImageIO processing error: ", e);
            return e.getMessage();
        }
    }

}
