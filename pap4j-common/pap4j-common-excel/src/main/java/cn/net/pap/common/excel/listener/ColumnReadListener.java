package cn.net.pap.common.excel.listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ColumnReadListener extends AnalysisEventListener<Map<Integer, String>> {

    /**
     * 表头数据
     */
    private Map<Integer, String> headMap = new HashMap<>();

    /**
     * 数据体
     */
    private List<Map<Integer, String>> dataList = new ArrayList<>();

    /**
     * 这里会一行行的返回头
     *
     * @param headMap
     * @param context
     */
    @Override
    public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
        this.headMap = headMap.entrySet().stream().filter((e) -> e.getValue() != null).collect(Collectors.toMap(
                (e) -> e.getKey(),
                (e) -> e.getValue()));
    }

    /**
     * 这个每一条数据解析都会来调用
     *
     * @param data    one row value. Is is same as {@link AnalysisContext#readRowHolder()}
     * @param context
     */
    @Override
    public void invoke(Map<Integer, String> data, AnalysisContext context) {
        dataList.add(data);
    }

    /**
     * 所有数据解析完成了 都会来调用
     *
     * @param context
     */
    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {

    }

    public Map<Integer, String> getHeadMap() {
        return headMap;
    }

    public List<Map<Integer, String>> getDataList() {
        return dataList;
    }

}
