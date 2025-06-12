package cn.net.pap.example.proguard.util;

import cn.net.pap.example.proguard.service.impl.NumberSegmentService;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

public class NumberSegmentUtil {

    private static final ConcurrentHashMap<String, Queue<String>> segmentCacheMap = new ConcurrentHashMap<>();

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


