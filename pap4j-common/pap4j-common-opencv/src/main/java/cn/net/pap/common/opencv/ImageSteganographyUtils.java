package cn.net.pap.common.opencv;

import java.awt.image.BufferedImage;

/**
 * 图像隐写
 */
public class ImageSteganographyUtils {

    // 将信息编码到图像中
    public static BufferedImage embedMessage(BufferedImage image, String message) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);

        // 将字符串转换为二进制
        StringBuilder binaryMessage = new StringBuilder();
        for (char c : message.toCharArray()) {
            binaryMessage.append(String.format("%8s", Integer.toBinaryString(c)).replace(' ', '0'));
        }

        int messageLength = binaryMessage.length();
        int pixelIndex = 0;
        int bitIndex = 0;

        // 遍历图像像素，将信息编码进LSB中
        while (bitIndex < messageLength && pixelIndex < pixels.length) {
            int pixel = pixels[pixelIndex];
            int red = (pixel >> 16) & 0xFF;
            int green = (pixel >> 8) & 0xFF;
            int blue = pixel & 0xFF;

            // 将信息编码进RGB分量的最低位
            if (bitIndex < messageLength) {
                red = (red & 0xFE) | (binaryMessage.charAt(bitIndex++) - '0');
            }
            if (bitIndex < messageLength) {
                green = (green & 0xFE) | (binaryMessage.charAt(bitIndex++) - '0');
            }
            if (bitIndex < messageLength) {
                blue = (blue & 0xFE) | (binaryMessage.charAt(bitIndex++) - '0');
            }

            // 更新像素值
            pixels[pixelIndex] = (red << 16) | (green << 8) | blue;
            pixelIndex++;
        }

        // 创建新的图像
        BufferedImage stegoImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        stegoImage.setRGB(0, 0, width, height, pixels, 0, width);
        return stegoImage;
    }

    // 从图像中提取隐藏的信息  请注意隐写信息已‘.’结尾。
    public static String extractMessage(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);

        StringBuilder binaryMessage = new StringBuilder();

        // 提取LSB中的信息
        for (int pixel : pixels) {
            int red = (pixel >> 16) & 0xFF;
            int green = (pixel >> 8) & 0xFF;
            int blue = pixel & 0xFF;

            binaryMessage.append(red & 1);
            binaryMessage.append(green & 1);
            binaryMessage.append(blue & 1);
        }

        // 将二进制信息转换为字符串
        StringBuilder message = new StringBuilder();
        for (int i = 0; i < binaryMessage.length(); i += 8) {
            int ascii = Integer.parseInt(binaryMessage.substring(i, i + 8), 2);
            message.append((char) ascii);
            if((char)ascii == '.') {
                break;
            }
        }
        return message.toString();
    }


}
