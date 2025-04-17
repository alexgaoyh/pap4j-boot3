package cn.net.pap.common.excel;

import cn.net.pap.common.excel.dto.ImportDTO;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.*;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class POIUtilTest {

    // @Test
    public void testReadExcelWithImages() throws IOException {
        String excelFilePath = "C:\\Users\\86181\\Desktop\\picture.xlsx";
        List<ImportDTO> importDTOS = readExcelWithImages(excelFilePath);
        for (ImportDTO dto : importDTOS) {
            System.out.println(dto);
            try (FileOutputStream fos = new FileOutputStream("C:\\Users\\86181\\Desktop\\" + dto.getName() + "." + dto.getImageType())) {
                fos.write(dto.getPicture());
            }
        }
    }

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

}
