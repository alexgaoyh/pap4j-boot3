package cn.net.pap.common.excel;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class ExcelCopyUtil {


    /**
     * 指定 源excel、目标excel、目标excel的sheet名称，将 小于 offset 偏移量的行全部复制，接下来制定行数 withinGroupLength 的数据当成一组，复制为 numberOfGroup 组。
     * @param sourceFilePath    源/模板 xlsx 的绝对路径
     * @param destFilePath  目标xlsx的绝对路径
     * @param targetSheetName   目标sheet的名称
     * @param sourceRowNum 0     源/模板 xlsx 的需复制的行号
     * @param destRowNum 0      目标 xlsx 的行号
     */
    public static void copyRowWithStyle(String sourceFilePath, String destFilePath, String targetSheetName, int sourceRowNum, int destRowNum) {
        try {
            FileInputStream fis = new FileInputStream(sourceFilePath);
            Workbook sourceWorkbook = WorkbookFactory.create(fis);
            Sheet sourceSheet = sourceWorkbook.getSheetAt(0);

            Workbook destWorkbook = WorkbookFactory.create(true);
            Sheet destSheet = destWorkbook.createSheet(targetSheetName);

            Row sourceRow = sourceSheet.getRow(sourceRowNum);
            Row destRow = destSheet.createRow(destRowNum);
            copyRowHeight(sourceRow, destRow);

            if (sourceRow != null) {
                for (int i = 0; i < sourceRow.getLastCellNum(); i++) {
                    Cell sourceCell = sourceRow.getCell(i);
                    Cell destCell = destRow.createCell(i);

                    copyCellValue(sourceCell, destCell);
                    copyCellStyle(sourceCell, destCell, destWorkbook);
                    copyColumnWidth(sourceSheet, destSheet, i);
                }
            }

            copyMergedRegions(sourceSheet, destSheet, sourceRowNum, destRowNum);

            FileOutputStream fos = new FileOutputStream(destFilePath);
            destWorkbook.write(fos);

            fis.close();
            fos.close();
            sourceWorkbook.close();
            destWorkbook.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 指定 源excel、目标excel、目标excel的sheet名称，将 小于 offset 偏移量的行全部复制，接下来制定行数 withinGroupLength 的数据当成一组，复制为 numberOfGroup 组。
     * @param sourceFilePath    源/模板 xlsx 的绝对路径
     * @param destFilePath  目标xlsx的绝对路径
     * @param targetSheetName   目标sheet的名称
     * @param offset        8
     * @param withinGroupLength 9
     * @param numberOfGroup 2
     */
    public static void copyRowWithStyleInGroup(String sourceFilePath, String destFilePath, String targetSheetName, int offset, int withinGroupLength, int numberOfGroup) {
        try {
            FileInputStream fis = new FileInputStream(sourceFilePath);
            Workbook sourceWorkbook = WorkbookFactory.create(fis);
            Sheet sourceSheet = sourceWorkbook.getSheetAt(0);

            Workbook destWorkbook = WorkbookFactory.create(true);
            Sheet destSheet = destWorkbook.createSheet(targetSheetName);

            for(int offsetIdx = 0; offsetIdx < offset; offsetIdx++) {
                Row sourceRow = sourceSheet.getRow(offsetIdx);
                Row destRow = destSheet.createRow(offsetIdx);
                copyRowHeight(sourceRow, destRow);

                if (sourceRow != null) {
                    for (int i = 0; i < sourceRow.getLastCellNum(); i++) {
                        Cell sourceCell = sourceRow.getCell(i);
                        Cell destCell = destRow.createCell(i);

                        copyCellValue(sourceCell, destCell);
                        copyCellStyle(sourceCell, destCell, destWorkbook);
                        copyColumnWidth(sourceSheet, destSheet, i);
                    }
                }

                copyMergedRegions(sourceSheet, destSheet, offsetIdx, offsetIdx);
            }

            for(int numberOfGroupIdx = 1; numberOfGroupIdx <= numberOfGroup; numberOfGroupIdx++) {
                for(int withinGroupLengthIdx = 0; withinGroupLengthIdx < withinGroupLength; withinGroupLengthIdx++) {
                    int sourceRowIdx = offset + withinGroupLengthIdx;
                    int targetRowIdx = (offset) + (numberOfGroupIdx - 1) * withinGroupLength + withinGroupLengthIdx;

                    Row sourceRow = sourceSheet.getRow(sourceRowIdx);
                    Row destRow = destSheet.createRow(targetRowIdx);
                    copyRowHeight(sourceRow, destRow);

                    if (sourceRow != null) {
                        for (int i = 0; i < sourceRow.getLastCellNum(); i++) {
                            Cell sourceCell = sourceRow.getCell(i);
                            Cell destCell = destRow.createCell(i);

                            copyCellValue(sourceCell, destCell);
                            copyCellStyle(sourceCell, destCell, destWorkbook);
                            copyColumnWidth(sourceSheet, destSheet, i);
                        }
                    }

                    copyMergedRegions(sourceSheet, destSheet, sourceRowIdx, targetRowIdx);
                }
            }


            FileOutputStream fos = new FileOutputStream(destFilePath);
            destWorkbook.write(fos);

            fis.close();
            fos.close();
            sourceWorkbook.close();
            destWorkbook.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void copyRowHeight(Row sourceRow, Row destRow) {
        if (sourceRow != null && sourceRow.getHeight() != -1) {
            destRow.setHeight(sourceRow.getHeight());
        }
    }

    private static void copyCellValue(Cell sourceCell, Cell destCell) {
        if (sourceCell != null) {
            destCell.setCellValue(getCellValue(sourceCell));
        }
    }

    private static void copyCellStyle(Cell sourceCell, Cell destCell, Workbook destWorkbook) {
        if (sourceCell != null) {
            CellStyle sourceStyle = sourceCell.getCellStyle();
            CellStyle destStyle = destWorkbook.createCellStyle();
            destStyle.cloneStyleFrom(sourceStyle);
            destCell.setCellStyle(destStyle);
        }
    }

    private static void copyColumnWidth(Sheet sourceSheet, Sheet destSheet, int columnIndex) {
        destSheet.setColumnWidth(columnIndex, sourceSheet.getColumnWidth(columnIndex));
    }

    private static void copyMergedRegions(Sheet sourceSheet, Sheet destSheet, int sourceRowNum, int destRowNum) {
        for (int i = 0; i < sourceSheet.getNumMergedRegions(); i++) {
            CellRangeAddress mergedRegion = sourceSheet.getMergedRegion(i);
            if (isInRange(sourceRowNum, mergedRegion.getFirstRow(), mergedRegion.getLastRow())) {
                int destFirstRow = destRowNum;
                int destLastRow = destRowNum + (mergedRegion.getLastRow() - mergedRegion.getFirstRow());
                int destFirstCol = mergedRegion.getFirstColumn();
                int destLastCol = mergedRegion.getLastColumn();
                destSheet.addMergedRegion(new CellRangeAddress(destFirstRow, destLastRow, destFirstCol, destLastCol));
            }
        }
    }

    private static String getCellValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }

    private static boolean isInRange(int value, int start, int end) {
        return value >= start && value <= end;
    }
}
