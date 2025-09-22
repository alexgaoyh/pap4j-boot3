package cn.net.pap.common.pdf.jpg;

import javax.imageio.*;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

public class JpegDPIProcessor {

    private String formatName = "jpeg";

    public byte[] setDPI(BufferedImage image, int dpi) throws IOException {
        for (Iterator<ImageWriter> iw = ImageIO.getImageWritersByFormatName(formatName); iw.hasNext(); ) {
            ImageWriter writer = iw.next();
            ImageWriteParam writeParams = writer.getDefaultWriteParam();
            writeParams.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            writeParams.setCompressionQuality(1);
            ImageTypeSpecifier typeSpecifier = ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_RGB);
            IIOMetadata metadata = writer.getDefaultImageMetadata(typeSpecifier, writeParams);
            if (metadata.isReadOnly() || !metadata.isStandardMetadataFormatSupported()) {
                continue;
            }
            String metadataFormat = "javax_imageio_jpeg_image_1.0";
            IIOMetadataNode root = new IIOMetadataNode(metadataFormat);
            IIOMetadataNode jpegVariety = new IIOMetadataNode("JPEGvariety");
            IIOMetadataNode markerSequence = new IIOMetadataNode("markerSequence");
            IIOMetadataNode app0JFIF = new IIOMetadataNode("app0JFIF");
            app0JFIF.setAttribute("majorVersion", "1");
            app0JFIF.setAttribute("minorVersion", "2");
            app0JFIF.setAttribute("thumbWidth", "0");
            app0JFIF.setAttribute("thumbHeight", "0");
            app0JFIF.setAttribute("resUnits", "01");
            app0JFIF.setAttribute("Xdensity", String.valueOf(dpi));
            app0JFIF.setAttribute("Ydensity", String.valueOf(dpi));
            root.appendChild(jpegVariety);
            root.appendChild(markerSequence);
            jpegVariety.appendChild(app0JFIF);
            metadata.mergeTree(metadataFormat, root);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageOutputStream stream = null;
            try {
                stream = ImageIO.createImageOutputStream(out);
                writer.setOutput(stream);
                writer.write(metadata, new IIOImage(image, null, metadata), writeParams);
            } finally {
                stream.close();
            }
            return out.toByteArray();
        }
        return null;

    }
}
