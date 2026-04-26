package cn.net.pap.common.excel;

import cn.net.pap.common.excel.dto.ImportDTO;
import org.apache.commons.io.FileUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.*;
import org.junit.jupiter.api.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class POIUtilTest {

    private static final Logger log = LoggerFactory.getLogger(POIUtilTest.class);

    @Test
    public void testReadExcelWithImages() throws IOException {
        String excelFilePath = TestResourceUtil.getFile("pictures.xlsx").getAbsolutePath();
        List<ImportDTO> importDTOS = readExcelWithImages(excelFilePath);

        Path tempDir = Files.createTempDirectory("excel-test-");
        log.info("测试使用的临时目录:  {}", tempDir.toAbsolutePath());

        for (ImportDTO dto : importDTOS) {
            log.info("{}", dto);
            if(dto.getPicture() != null) {
                File tempFile = tempDir.resolve(dto.getName() + "." + dto.getImageType()).toFile();
                try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                    fos.write(dto.getPicture());
                }
                tempFile.deleteOnExit();
            }
        }
        FileUtils.deleteDirectory(tempDir.toFile());
    }

    /**
     * 这个方法的图像，都是浮动图像。也就是类似于把图像拖拽到 excel 里面，然后拖拽放到某一个单元格里面。不是通过插入图像的方式。
     * @param filePath
     * @return
     * @throws IOException
     */
    public static List<ImportDTO> readExcelWithImages(String filePath) throws IOException {
        List<ImportDTO> result = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            XSSFDrawing drawing = (XSSFDrawing) sheet.createDrawingPatriarch();

            List<XSSFShape> shapes = drawing.getShapes();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                Cell textCell = row.getCell(0);
                String text = (textCell != null) ? textCell.toString() : "";

                byte[] imageData = null;
                String imageType = "";
                for (XSSFShape shape : shapes) {
                    if (shape instanceof XSSFPicture) {
                        XSSFPicture picture = (XSSFPicture) shape;
                        XSSFClientAnchor anchor = picture.getClientAnchor();

                        if (anchor.getRow1() == row.getRowNum()) {
                            if (anchor.getCol1() == 1) {
                                imageData = picture.getPictureData().getData();
                                switch (picture.getPictureData().getPictureType()) {
                                    case Workbook.PICTURE_TYPE_PNG:
                                        imageType = "png";
                                        break;
                                    case Workbook.PICTURE_TYPE_JPEG:
                                        imageType = "jpeg";
                                        break;
                                    case Workbook.PICTURE_TYPE_DIB:
                                        imageType = "bmp";
                                        break;
                                    case Workbook.PICTURE_TYPE_EMF:
                                        imageType = "emf";
                                        break;
                                    case Workbook.PICTURE_TYPE_WMF:
                                        imageType = "wmf";
                                        break;
                                    default:
                                        imageType = "png";
                                }
                                break;
                            }
                        }
                    }
                }
                result.add(new ImportDTO(text, imageData, imageType));
            }
        }

        return result;
    }

    @Test
    public void testReadExcelWithEmbeddedImages() throws IOException {
        // 如果有专门的包含“嵌入图片”的测试文件，可将此处替换为 "embedded_pictures.xlsx"
        String excelFilePath = TestResourceUtil.getFile("embedded_pictures.xlsx").getAbsolutePath();
        List<ImportDTO> importDTOS = readExcelWithEmbeddedImages(excelFilePath);

        Path tempDir = Files.createTempDirectory("excel-embedded-test-");
        log.info("测试使用的临时目录:  {}", tempDir.toAbsolutePath());

        for (ImportDTO dto : importDTOS) {
            log.info("{}", dto);
            if(dto.getPicture() != null) {
                File tempFile = tempDir.resolve(dto.getName() + "." + dto.getImageType()).toFile();
                try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                    fos.write(dto.getPicture());
                }
                tempFile.deleteOnExit();
            }
        }
        FileUtils.deleteDirectory(tempDir.toFile());
    }

    /**
     * 这个方法用于提取“嵌入单元格”（Place in Cell）的图像。
     * 由于 Apache POI 当前的原生 API 尚未提供将嵌入式图片与对应单元格精确关联的直接方法，
     * （其映射关系存储底层的 xl/cellimages.xml 中）。
     * 此增量代码演示如何通过获取整个 Workbook 的所有图片集合，来读取其中包含的嵌入图像数据。
     * 
     * @param filePath
     * @return
     * @throws IOException
     */
    public static List<ImportDTO> readExcelWithEmbeddedImages(String filePath) throws IOException {
        List<ImportDTO> result = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            // 获取工作簿中的所有图片（包含浮动图片和嵌入图片的数据实体）
            List<? extends org.apache.poi.ss.usermodel.PictureData> allPictures = workbook.getAllPictures();

            for (int i = 0; i < allPictures.size(); i++) {
                org.apache.poi.ss.usermodel.PictureData pictureData = allPictures.get(i);
                byte[] imageData = pictureData.getData();
                String imageType = pictureData.suggestFileExtension();
                
                // 暂时以索引作为图片命名，如需精确匹配单元格需解析底层 XML 关系。
                result.add(new ImportDTO("embedded_image_" + i, imageData, imageType));
            }
        }

        return result;
    }

}
