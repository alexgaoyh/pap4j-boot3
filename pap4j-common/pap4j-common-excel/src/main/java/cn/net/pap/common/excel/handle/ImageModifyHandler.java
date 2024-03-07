package cn.net.pap.common.excel.handle;

import com.alibaba.excel.metadata.Head;
import com.alibaba.excel.metadata.data.ImageData;
import com.alibaba.excel.metadata.data.WriteCellData;
import com.alibaba.excel.write.handler.CellWriteHandler;
import com.alibaba.excel.write.metadata.holder.WriteSheetHolder;
import com.alibaba.excel.write.metadata.holder.WriteTableHolder;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;

import java.util.List;
import java.util.Objects;

/**
 * 图像填充完合并后的单元格 - 如果需要将图像完成填充至一个被合并的单元格，可使用如下方式
 * 使用方式： WriteSheet writeSheet = EasyExcel.writerSheet("Sheet1").registerWriteHandler(new ImageModifyHandler()).build();
 */
public class ImageModifyHandler implements CellWriteHandler {
    /**
     * 后单元格数据转换
     *
     * @param writeSheetHolder 写单夹
     * @param writeTableHolder 写表夹
     * @param cellData         单元格数据
     * @param cell             细胞
     * @param head             头
     * @param relativeRowIndex 相对行索引
     * @param isHead           是头
     */
    @Override
    public void afterCellDataConverted(WriteSheetHolder writeSheetHolder, WriteTableHolder writeTableHolder,
                                       WriteCellData<?> cellData, Cell cell, Head head, Integer relativeRowIndex, Boolean isHead) {
        boolean noImageValue = Objects.isNull(cellData) || cellData.getImageDataList() == null || cellData.getImageDataList().size() == 0;
        if (Objects.equals(Boolean.TRUE, isHead) || noImageValue) {
            return;
        }
        Sheet sheet = cell.getSheet();
        int mergeColumNum = getMergeColumNum(cell, sheet);
        int mergeRowNum = getMergeRowNum(cell, sheet);
        ImageData imageData = cellData.getImageDataList().get(0);
        imageData.setRelativeLastRowIndex(mergeRowNum - 1);
        imageData.setRelativeLastColumnIndex(mergeColumNum - 1);
        CellWriteHandler.super.afterCellDataConverted(writeSheetHolder, writeTableHolder, cellData, cell, head, relativeRowIndex, isHead);
    }


    /**
     * 得到合并行num
     *
     * @param cell  细胞
     * @param sheet 表
     * @return int
     */
    public static int getMergeRowNum(Cell cell, Sheet sheet) {
        int mergeSize = 1;
        List<CellRangeAddress> mergedRegions = sheet.getMergedRegions();
        for (CellRangeAddress cellRangeAddress : mergedRegions) {
            if (cellRangeAddress.isInRange(cell)) {
                //获取合并的行数
                mergeSize = cellRangeAddress.getLastRow() - cellRangeAddress.getFirstRow() + 1;
                break;
            }
        }
        return mergeSize;
    }

    /**
     * 得到合并列num
     *
     * @param cell  细胞
     * @param sheet 表
     * @return int
     */
    public static int getMergeColumNum(Cell cell, Sheet sheet) {
        int mergeSize = 1;
        List<CellRangeAddress> mergedRegions = sheet.getMergedRegions();
        for (CellRangeAddress cellRangeAddress : mergedRegions) {
            if (cellRangeAddress.isInRange(cell)) {
                //获取合并的列数
                mergeSize = cellRangeAddress.getLastColumn() - cellRangeAddress.getFirstColumn() + 1;
                break;
            }
        }
        return mergeSize;
    }

}
