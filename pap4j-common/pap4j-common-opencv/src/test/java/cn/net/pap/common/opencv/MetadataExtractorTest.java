package cn.net.pap.common.opencv;

import java.io.File;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import org.junit.jupiter.api.Test;

public class MetadataExtractorTest {

    @Test
    public void getTest() {
        try {
            // 读取图片文件
            File imageFile = TestResourceUtil.getFile("tiff1.jpg");
            if(imageFile.exists()){
                Metadata metadata = ImageMetadataReader.readMetadata(imageFile);
                for (Directory directory : metadata.getDirectories()) {
                    System.out.println("---- " + directory.getName() + " ----");
                    for (Tag tag : directory.getTags()) {
                        System.out.println(tag.getTagName() + " : " + tag.getDescription());
                    }
                }
                ExifSubIFDDirectory exifDirectory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
                if (exifDirectory != null) {
                    System.out.println("拍摄时间: " + exifDirectory.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
