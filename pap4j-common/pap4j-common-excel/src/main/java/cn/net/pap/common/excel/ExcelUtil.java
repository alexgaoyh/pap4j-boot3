package cn.net.pap.common.excel;

import cn.net.pap.common.excel.dto.CompareDTO;
import cn.net.pap.common.excel.listener.ColumnReadListener;
import cn.net.pap.common.excel.listener.HeadRowReadListener;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelReader;
import com.alibaba.excel.read.metadata.ReadSheet;

import java.util.*;
import java.util.stream.Collectors;

public class ExcelUtil {

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
            e.printStackTrace();
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

    public static Map<String, List<Map<String, Object>>> groupByField(List<Map<String, Object>> sourceRowList, List<String> sourceFieldList) {
        return sourceRowList.stream()
                .collect(Collectors.groupingBy(
                        row -> concatenateFieldValues(row, sourceFieldList),
                        Collectors.toList()
                ));
    }

    private static String concatenateFieldValues(Map<String, Object> row, List<String> sourceFieldList) {
        return sourceFieldList.stream()
                .map(field -> String.valueOf(row.get(field)))
                .collect(Collectors.joining("~~~"));
    }
}
