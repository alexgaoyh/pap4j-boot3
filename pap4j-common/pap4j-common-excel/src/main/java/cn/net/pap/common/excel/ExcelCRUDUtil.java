package cn.net.pap.common.excel;

import cn.net.pap.common.excel.dto.PageData;
import org.apache.poi.hssf.usermodel.HSSFDataFormat;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.NumberToTextConverter;
import org.apache.poi.util.IOUtils;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ExcelCRUDUtil {

    public static void createXlsx(String path, String sheetName) {
        new File(path).delete();
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet(sheetName);
        try {
            FileOutputStream fileOut = new FileOutputStream(path);
            workbook.write(fileOut);
            fileOut.close();
            workbook.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 当前只完成内容的模糊查询
    public static List<PageData> selectList(String path, String sheet) throws ParseException {
        List<PageData> res = vttInit(path, sheet);
        List<PageData> rs = new ArrayList<>();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        rs.add(res.get(0));
        for (int i = 1; i < res.size(); i++) {
            rs.add(res.get(i));
        }
        return rs;
    }

    public static void insert(PageData pd, String path, String sheets) throws IOException {
        XSSFWorkbook workbook = getExcelByPath(path);
        XSSFSheet sheet = workbook.getSheet(sheets);
        XSSFRow row = sheet.getRow(0);
        int cell_end = pd.size();
        int row_end = getExcelRealRow(sheet);// 这是行数
        row = sheet.createRow(row_end + 1);
        for (int i = 0; i < cell_end; i++) {
            row.createCell(i).setCellValue(pd.getStringByIdx(i));
        }
        FileOutputStream out = new FileOutputStream(path);
        workbook.write(out);
        out.close();
    }

    public static void delete(int rowIndex, String path, String sheets) throws IOException {
        XSSFWorkbook workbook = getExcelByPath(path);
        XSSFSheet sheet = workbook.getSheet(sheets);
        int lastRowNum = sheet.getLastRowNum();
        if (rowIndex >= 0 && rowIndex < lastRowNum)
            sheet.shiftRows(rowIndex + 1, lastRowNum, -1);// 将行号为rowIndex+1一直到行号为lastRowNum的单元格全部上移一行，以便删除rowIndex行
        if (rowIndex == lastRowNum) {
            XSSFRow removingRow = sheet.getRow(rowIndex);
            if (removingRow != null)
                sheet.removeRow(removingRow);
        }
        FileOutputStream out = new FileOutputStream(path);
        workbook.write(out);
        out.close();
    }

    /**
     * @param rowNum 行数
     * @param colNum 列数
     * @param value
     * @param path
     * @param sheets
     * @throws IOException
     */
    public static void update(int rowNum, int colNum, String value, String path, String sheets) throws IOException {
        XSSFWorkbook workbook = getExcelByPath(path);
        XSSFSheet sheet = workbook.getSheet(sheets);
        XSSFRow row = sheet.getRow(rowNum);
        XSSFCell cell = row.getCell(colNum);
        if (cell == null) {
            row.createCell(colNum).setCellValue(value);
        } else {
            row.getCell(colNum).setCellValue(value);
        }
        FileOutputStream out = new FileOutputStream(path);
        workbook.write(out);
        out.close();
    }

    public static List<PageData> vttInit(String path, String sheets) {
        List<PageData> rs = new ArrayList<>();
        XSSFWorkbook workbook = getExcelByPath(path);
        XSSFSheet sheet = workbook.getSheet(sheets);
        // 得到Excel表格
        XSSFRow row = sheet.getRow(0);
        // 得到Excel工作表指定行的单元格
        XSSFCell cell = row.getCell(0);
        int cell_end = row.getLastCellNum();// 这个就是列数(标题)
        int row_end = getExcelRealRow(sheet);// 这是行数
        for (int i = 0; i <= row_end; i++) {
            row = sheet.getRow(i);
            PageData exd = new PageData();
            for (int j = 0; j <= cell_end + 1; j++) {
                if (row != null && i == 0 && j == cell_end) {
                    exd.put(j, "");
                    continue;
                } else if (row != null && i == 0 && j == cell_end + 1) {
                    exd.put(j, "");
                    continue;
                } else if (row != null && j < cell_end)
                    cell = row.getCell(j);
                else if (i != 0 && j == cell_end)
                    continue;
                if (cell != null)
                    exd.put(j, formatCell(cell));
                else {
                    exd.put(j, "");
                }
            }
            exd.put("xh", i + "");
            rs.add(exd);
        }
        return rs;
    }

    // 获取Excel表的真实行数
    public static int getExcelRealRow(Sheet sheet) {
        boolean flag = false;
        for (int i = 1; i <= sheet.getLastRowNum(); ) {
            Row r = sheet.getRow(i);
            if (r == null) {
                // 如果是空行（即没有任何数据、格式），直接把它以下的数据往上移动
                sheet.shiftRows(i + 1, sheet.getLastRowNum(), -1);
                continue;
            }
            flag = false;
            for (Cell c : r) {
                if (c.getCellType() != CellType.BLANK) {
                    flag = true;
                    break;
                }
            }
            if (flag) {
                i++;
                continue;
            } else {
                // 如果是空白行（即可能没有数据，但是有一定格式）
                if (i == sheet.getLastRowNum())// 如果到了最后一行，直接将那一行remove掉
                    sheet.removeRow(r);
                else// 如果还没到最后一行，则数据往上移一行
                    sheet.shiftRows(i + 1, sheet.getLastRowNum(), -1);
            }
        }
        return sheet.getLastRowNum();
    }

    /**
     * 通过文件路劲获取excel文件
     *
     * @param path
     * @return
     */
    public static XSSFWorkbook getExcelByPath(String path) {
        try {
            byte[] buf = IOUtils.toByteArray(new FileInputStream(path));
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(buf);
            XSSFWorkbook workbook = new XSSFWorkbook(byteArrayInputStream);
            return workbook;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String formatCell(Cell cell) {
        String ret;
        switch (cell.getCellType()) {
            case STRING:
                ret = cell.getStringCellValue();
                break;
            case FORMULA:
                Workbook wb = cell.getSheet().getWorkbook();
                CreationHelper crateHelper = wb.getCreationHelper();
                FormulaEvaluator evaluator = crateHelper.createFormulaEvaluator();
                ret = formatCell(evaluator.evaluateInCell(cell));
                break;
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {// 处理日期格式、时间格式
                    SimpleDateFormat sdf = null;
                    if (cell.getCellStyle().getDataFormat() == HSSFDataFormat.getBuiltinFormat("h:mm")) {
                        sdf = new SimpleDateFormat("HH:mm");
                    } else {// 日期
                        sdf = new SimpleDateFormat("yyyy-MM-dd");
                    }
                    Date date = cell.getDateCellValue();
                    ret = sdf.format(date);
                } else if (cell.getCellStyle().getDataFormat() == 58) {
                    // 处理自定义日期格式：m月d日(通过判断单元格的格式id解决，id的值是58)
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                    double value = cell.getNumericCellValue();
                    Date date = org.apache.poi.ss.usermodel.DateUtil.getJavaDate(value);
                    ret = sdf.format(date);
                } else {
                    ret = NumberToTextConverter.toText(cell.getNumericCellValue());
                }
                break;
            case BLANK:
                ret = "";
                break;
            case BOOLEAN:
                ret = String.valueOf(cell.getBooleanCellValue());
                break;
            case ERROR:
                ret = null;
                break;
            default:
                ret = null;
        }
        return ret; // 有必要自行trim
    }

}
