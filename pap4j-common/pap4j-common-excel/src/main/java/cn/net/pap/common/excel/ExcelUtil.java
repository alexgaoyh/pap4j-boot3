package cn.net.pap.common.excel;

import cn.net.pap.common.excel.dto.CompareDTO;
import cn.net.pap.common.excel.dto.SimpleTriple;
import cn.net.pap.common.excel.listener.ColumnReadListener;
import cn.net.pap.common.excel.listener.ColumnReadWithMergeListener;
import cn.net.pap.common.excel.listener.HeadRowReadListener;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelReader;
import com.alibaba.excel.enums.CellExtraTypeEnum;
import com.alibaba.excel.metadata.CellExtra;
import com.alibaba.excel.read.metadata.ReadSheet;
import com.alibaba.excel.util.StringUtils;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class ExcelUtil {

    private static final Logger log = LoggerFactory.getLogger(ExcelUtil.class);

    /**
     * 获得表头信息
     *
     * @param fileAbsolutePath
     * @return
     */
    public static Map<String, List<String>> getHeadMap(String fileAbsolutePath) {
        Map<String, List<String>> resultData = new LinkedHashMap<>();

        try {
            ExcelReader excelReader = EasyExcel.read(fileAbsolutePath).build();
            List<ReadSheet> readSheets = excelReader.excelExecutor().sheetList();
            for (ReadSheet readSheet : readSheets) {

                ExcelReader excelReaderTmp = EasyExcel.read(fileAbsolutePath).build();

                HeadRowReadListener headRowReadListener = new HeadRowReadListener();

                ReadSheet readSheetTmp = EasyExcel.readSheet(readSheet.getSheetNo()).headRowNumber(1).registerReadListener(headRowReadListener).build();

                excelReaderTmp.read(readSheetTmp);
                excelReaderTmp.finish();

                resultData.put(readSheet.getSheetName(), new ArrayList<String>(headRowReadListener.getHeadMap().values()));
            }
            excelReader.finish();
        } catch (Exception e) {
            log.error("getHeadMap", e);
        }
        return resultData;
    }

    /**
     * 根据传入的 excel 绝对路径，获取下方指定的 sheetName，并进行数据值获取，每一条数据增加一个名为 indexNoConstant 的顺序号(为null的话忽略顺序号)
     *
     * @param fileAbsolutePath
     * @param sheetName
     * @return List 数据集合， 内部 map 有序(同 rowList 顺序)
     */
    public static List<Map<String, Object>> getRowList(String fileAbsolutePath, String sheetName, String indexNoConstantKey) {
        List<Map<String, Object>> resultData = new ArrayList<>();
        try {
            ColumnReadListener archiverColumnReadListener = new ColumnReadListener();
            EasyExcel.read(fileAbsolutePath, archiverColumnReadListener).ignoreEmptyRow(true).sheet(sheetName).doReadSync();
            if(archiverColumnReadListener.getHeadMap() != null
                    && archiverColumnReadListener.getDataList() != null) {

                for(int idx = 0; idx < archiverColumnReadListener.getDataList().size(); idx++) {
                    Map<Integer, String> dataMap = archiverColumnReadListener.getDataList().get(idx);
                    Map<String, Object> tmp = new LinkedHashMap<>();
                    for(Map.Entry<Integer, String> headEntry : archiverColumnReadListener.getHeadMap().entrySet()) {
                        String value = dataMap.get(headEntry.getKey());
                        tmp.put(headEntry.getValue(), value);
                    }
                    if(indexNoConstantKey != null && !"".equals(indexNoConstantKey)) {
                        tmp.put(indexNoConstantKey, idx + 1);
                    }
                    resultData.add(tmp);
                }

            }
        } catch (Exception error) {
            error.printStackTrace();
        }
        return resultData;
    }

    /**
     * 同 getRowList ， 增加了合并的单元格的数据重新维护的情况。  合并的单元格在读取数据之后，类似转平铺的效果，把null进行填充。
     * @param fileAbsolutePath
     * @param sheetName
     * @param indexNoConstantKey
     * @return
     */
    public static List<Map<String, Object>> getRowListWithMergeCell(String fileAbsolutePath, String sheetName, String indexNoConstantKey) {
        List<Map<String, Object>> resultData = new ArrayList<>();
        try {
            ColumnReadWithMergeListener archiverColumnReadListener = new ColumnReadWithMergeListener();
            EasyExcel.read(fileAbsolutePath, archiverColumnReadListener).extraRead(CellExtraTypeEnum.MERGE).ignoreEmptyRow(true).sheet(sheetName).doReadSync();
            if(archiverColumnReadListener.getHeadMap() != null
                    && archiverColumnReadListener.getDataList() != null) {
                for(int idx = 0; idx < archiverColumnReadListener.getDataList().size(); idx++) {
                    Map<String, Object> tmp = new LinkedHashMap<>();
                    for(Map.Entry<Integer, String> headEntry : archiverColumnReadListener.getHeadMap().entrySet()) {
                        String value = checkIsMergedCellAndGetValue(archiverColumnReadListener, idx, headEntry.getKey(), 1);
                        tmp.put(headEntry.getValue(), value);
                    }
                    if(indexNoConstantKey != null && !"".equals(indexNoConstantKey)) {
                        tmp.put(indexNoConstantKey, idx + 1);
                    }
                    resultData.add(tmp);
                }

            }
        } catch (Exception error) {
            error.printStackTrace();
        }
        return resultData;
    }

    public static List<Map<String, Object>> getOneToManyRowList(
            String sourceFileAbsolutePath, String sourceSheetName,
            String targetFileAbsolutePath, String targetSheetName,
            List<CompareDTO> fieldCompareDTOLists,
            String indexNoConstantKey,
            String childConstantKey) {
        List<Map<String, Object>> returnMapList = new ArrayList<>();

        List<Map<String, Object>> sourceRowList = getRowList(sourceFileAbsolutePath, sourceSheetName, indexNoConstantKey);
        List<Map<String, Object>> targetRowList = getRowList(targetFileAbsolutePath, targetSheetName, indexNoConstantKey);

        List<String> sourceFieldList = fieldCompareDTOLists.stream().map(CompareDTO::getSourceField).collect(Collectors.toList());
        List<String> targetFieldList = fieldCompareDTOLists.stream().map(CompareDTO::getTargetField).collect(Collectors.toList());

        Map<String, List<Map<String, Object>>> sourceRowListGroup = groupByField(sourceRowList, sourceFieldList);
        Map<String, List<Map<String, Object>>> targetRowListGroup = groupByField(targetRowList, targetFieldList);

        sourceRowListGroup.forEach((key, value) -> {
            if(value != null && value.size() == 1) {
                Map<String, Object> headMap = value.get(0);
                if(targetRowListGroup.containsKey(key) && childConstantKey != null && !"".equals(childConstantKey)) {
                    headMap.put(childConstantKey, targetRowListGroup.get(key));
                }
                returnMapList.add(headMap);
            }

        });

        return returnMapList;
    }

    /**
     * 把从 excel 中读取的 rowMapList转换为三元组信息，其中 subjectMapKey 对应的数据当成主语
     * @param rowMapList    excel提取的数据
     * @param subjectMapKey 主语对应的key
     * @param fileterMapKeyList 需要过滤的map
     * @return
     */
    public static List<SimpleTriple<String, String, Object>> convert2SimpleTriple(List<Map<String, Object>> rowMapList, String subjectMapKey, List<String> fileterMapKeyList) {
        List<SimpleTriple<String, String, Object>> returnTripleList = new ArrayList<>();

        if(rowMapList != null && rowMapList.size() > 0) {
            for(Map<String, Object> rowMap : rowMapList) {
                if(rowMap.containsKey(subjectMapKey)) {
                    String subjectValue = rowMap.get(subjectMapKey).toString();
                    if(!StringUtils.isEmpty(subjectValue)) {
                        for(Map.Entry<String, Object> entry : rowMap.entrySet()) {
                            if(!entry.getKey().equals(subjectMapKey) && entry.getValue() != null && !fileterMapKeyList.contains(entry.getKey())) {
                                SimpleTriple tmp = new SimpleTriple(subjectValue, entry.getKey(), entry.getValue());
                                returnTripleList.add(tmp);
                            }
                        }
                    }
                }
            }
        }

        return returnTripleList;
    }

    /**
     * 隐藏行 查找指定sheet中所有隐藏的行号
     * @param sourceFileAbsolutePath
     * @param sourceSheetName
     * @return
     * @throws IOException
     */
    public static List<Integer> findHiddenRows(String sourceFileAbsolutePath, String sourceSheetName) {
        List<Integer> hiddenRows = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(sourceFileAbsolutePath);
             Workbook sourceWorkbook = WorkbookFactory.create(fis)) {

            Sheet sheet = sourceWorkbook.getSheet(sourceSheetName);
            if (sheet == null) {
                throw new IllegalArgumentException("Sheet '" + sourceSheetName + "' not found");
            }

            // 使用物理行数而不是逻辑行数范围
            int firstRowNum = sheet.getFirstRowNum();
            int lastRowNum = sheet.getLastRowNum();

            for (int rowNum = firstRowNum; rowNum <= lastRowNum; rowNum++) {
                Row row = sheet.getRow(rowNum);
                // 检查行是否存在且被隐藏
                if (row != null && row.getZeroHeight()) {
                    hiddenRows.add(rowNum);
                }
            }

        } catch (FileNotFoundException e) {
            throw new RuntimeException("File not found: " + sourceFileAbsolutePath, e);
        } catch (IOException e) {
            throw new RuntimeException("Error reading file: " + sourceFileAbsolutePath, e);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected error while processing Excel file", e);
        }

        return hiddenRows;
    }


    public static Map<String, List<Map<String, Object>>> groupByField(List<Map<String, Object>> sourceRowList, List<String> sourceFieldList) {
        return sourceRowList.parallelStream()
                .collect(Collectors.groupingBy(
                        row -> concatenateFieldValues(row, sourceFieldList),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    private static String concatenateFieldValues(Map<String, Object> row, List<String> sourceFieldList) {
        return sourceFieldList.parallelStream()
                .map(field -> String.valueOf(row.get(field)))
                .collect(Collectors.joining("~~~"));
    }

    /**
     * 判断是否是被合并的单元格，如果是的话，把应该存在的值查询出来返回，并填充值，类似转平铺的效果
     * @param archiverColumnReadListener    EasyExcel 解析出来的数据信息
     * @param rowIdx    当前的行号
     * @param columnIdx 当前的列号
     * @param HEAD_NUMBER 表头的行数
     * @return
     */
    private static String checkIsMergedCellAndGetValue(ColumnReadWithMergeListener archiverColumnReadListener, Integer rowIdx, Integer columnIdx, Integer HEAD_NUMBER) {
        // 真实的 所属行/所属列 编号， 如果单元格被合并，那么需要取出来正确的数据值。
        Integer realRowIdx = rowIdx;
        Integer realColumnIdx = columnIdx;
        List<CellExtra> cellExtraMergeList = archiverColumnReadListener.getCellExtraMergeList();
        if(cellExtraMergeList != null && cellExtraMergeList.size() > 0) {
            for(CellExtra cellExtra : cellExtraMergeList) {
                if(cellExtra.getFirstColumnIndex() == cellExtra.getLastColumnIndex()) {
                    if(cellExtra.getFirstRowIndex() - HEAD_NUMBER <= rowIdx &&
                            cellExtra.getLastRowIndex() - HEAD_NUMBER >= rowIdx &&
                            cellExtra.getFirstColumnIndex() <= columnIdx &&
                            cellExtra.getLastColumnIndex() >= columnIdx) {
                        realRowIdx = cellExtra.getFirstRowIndex() - HEAD_NUMBER;
                        realColumnIdx = cellExtra.getFirstColumnIndex();
                        break;
                    }
                }
                if(cellExtra.getFirstRowIndex() == cellExtra.getLastRowIndex()) {
                    if(cellExtra.getFirstRowIndex() - HEAD_NUMBER <= rowIdx &&
                            cellExtra.getLastRowIndex() - HEAD_NUMBER >= rowIdx &&
                            cellExtra.getFirstColumnIndex() <= columnIdx &&
                            cellExtra.getLastColumnIndex() >= columnIdx) {
                        realRowIdx = cellExtra.getFirstRowIndex() - HEAD_NUMBER;
                        realColumnIdx = cellExtra.getFirstColumnIndex();
                        break;
                    }
                }
            }
        }
        return archiverColumnReadListener.getDataList().get(realRowIdx).get(realColumnIdx);
    }
}
