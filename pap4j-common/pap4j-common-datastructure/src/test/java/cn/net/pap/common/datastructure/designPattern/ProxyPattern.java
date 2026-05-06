package cn.net.pap.common.datastructure.designPattern;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProxyPattern {

    private static final Logger log = LoggerFactory.getLogger(ProxyPattern.class);

    interface Image {
        void display();
    }

    class RealImage implements Image {

        private String fileName;

        public RealImage(String fileName) {
            this.fileName = fileName;
            loadFromDisk(fileName);
        }

        @Override
        public void display() {
            log.info("Displaying {}", fileName);
        }

        private void loadFromDisk(String fileName) {
            log.info("Loading {}", fileName);
        }
    }

    class ProxyImage implements Image {

        private RealImage realImage;

        private String fileName;

        public ProxyImage(String fileName) {
            this.fileName = fileName;
        }

        @Override
        public void display() {
            if (realImage == null) {
                realImage = new RealImage(fileName);
            }
            realImage.display();
        }
    }

    @Test
    public void test() {
        Image image = new ProxyImage("test_10mb.jpg");

        // 图像将从磁盘加载
        image.display();
        log.info("");
        // 图像不需要从磁盘加载
        image.display();
    }
}
