package cn.net.pap.common.excel.listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;

import java.util.LinkedHashMap;
import java.util.Map;

public class HeadRowReadListener extends AnalysisEventListener<Map<Integer, String>> {

    /**
     * 表头数据
     */
    private Map<Integer, String> headMap = new LinkedHashMap<>();


    /**
     * 初始化为 true ，解析完一条数据之后改为 false， 然后 hasNext() 函数就不再解析了。
     */
    private boolean hasNext = true;


    /**
     * 这里会一行行的返回头
     *
     * @param headMap
     * @param context
     */
    @Override
    public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
        this.headMap = headMap;
    }

    /**
     * 这个每一条数据解析都会来调用
     *
     * @param data    one row value. Is is same as {@link AnalysisContext#readRowHolder()}
     * @param context
     */
    @Override
    public void invoke(Map<Integer, String> data, AnalysisContext context) {
        if (!hasNext) {
            return;
        }
        hasNext = false;
    }


    @Override
    public boolean hasNext(AnalysisContext context) {
        return hasNext;
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
}
