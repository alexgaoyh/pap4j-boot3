package cn.net.pap.example.async.constant;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class AsyncConstant {

    // 存储取消标志
    public static final Map<String, AtomicBoolean> cancellationFlags = new ConcurrentHashMap<>();

    // 存储任务进度 (0-100)
    public static final Map<String, AtomicInteger> taskProgress = new ConcurrentHashMap<>();

}
