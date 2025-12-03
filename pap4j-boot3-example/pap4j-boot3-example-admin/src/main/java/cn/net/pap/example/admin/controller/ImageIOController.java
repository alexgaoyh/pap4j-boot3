package cn.net.pap.example.admin.controller;

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
public class ImageIOController {

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
                        System.out.println("ImageReader SPI Class: " + spi.getClass().getName());
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
            e.printStackTrace();
            return e.getMessage();
        }
    }

}
