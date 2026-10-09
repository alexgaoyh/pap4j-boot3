package cn.net.pap.example.proguard.util;

import cn.net.pap.example.proguard.service.impl.NumberSegmentService;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

public class NumberSegmentUtil {

    private static final ConcurrentHashMap<String, Queue<String>> segmentCacheMap = new ConcurrentHashMap<>();

    /**
     * 获取下一个号码（号段缓存不足时自动加载）。
     *
     * @param segmentName 号段名称
     * @return 号码字符串
     */
    public static synchronized String getNextNumber(String segmentName) {
        Queue<String> queue = segmentCacheMap.computeIfAbsent(segmentName, key -> new LinkedList<>());

        if (queue.isEmpty()) {
            loadSegments(segmentName, queue);
        }

        return queue.poll();
    }

    private static void loadSegments(String segmentName, Queue<String> queue) {
        NumberSegmentService numberSegmentService = SpringUtils.getBean(NumberSegmentService.class);
        numberSegmentService.loadSegments(segmentName, queue);
    }
}


