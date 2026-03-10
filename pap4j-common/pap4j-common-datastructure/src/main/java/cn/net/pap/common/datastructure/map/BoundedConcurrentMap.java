package cn.net.pap.common.datastructure.map;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 防 OOM 的有界并发 Map
 * 采用“软限制 (Soft Bound)”机制，在高并发下保护内存不被击穿，同时避免了全局锁的性能损耗。
 */
public class BoundedConcurrentMap<K, V> {

    // 底层依然使用高性能的 ConcurrentHashMap
    private final ConcurrentHashMap<K, V> map;
    // 最大容量阈值
    private final long maxCapacity;

    public BoundedConcurrentMap(long maxCapacity) {
        if (maxCapacity <= 0) {
            throw new IllegalArgumentException("最大容量必须大于 0");
        }
        this.map = new ConcurrentHashMap<>();
        this.maxCapacity = maxCapacity;
    }

    /**
     * 尝试放入元素（普通 put 的安全替代品）
     *
     * map.mappingCount() 和 map.putIfAbsent() 之间是 非原子操作。
     *
     * 高并发下，多个线程可能同时通过 map.mappingCount() < maxCapacity 的检查，然后几乎同时调用 putIfAbsent。
     *
     * 这就导致最终容量略微超过 maxCapacity，但不会大幅超过（比如 100 → 101~102）。
     *
     * 这正是你测试里提到的 “软限制(Soft Bound)” 的特性。
     *
     * @return true: 放入或更新成功；false: 容量已满，拒绝放入新元素。
     */
    public boolean tryPut(K key, V value) {
        if (key == null || value == null) {
            throw new NullPointerException("BoundedConcurrentMap 不支持 null 键或值");
        }

        // 1. 如果 Key 已经存在，直接更新（因为不增加总容量，永远允许）
        if (map.containsKey(key)) {
            map.put(key, value);
            return true;
        }

        // 2. 如果是新 Key，先进行容量探针检查
        if (map.mappingCount() >= maxCapacity) {
            return false; // 容量溢出，触发熔断，拒绝写入
        }

        // 3. 尝试安全放入（存在极微小并发超限可能，但作为防 OOM 兜底完全达标）
        map.putIfAbsent(key, value);
        return true;
    }

    /**
     * 包装原生的 compute 方法，注入容量拦截逻辑。（限流器的核心依赖）
     */
    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        return map.compute(key, (k, oldValue) -> {
            // 容量熔断拦截：如果是全新的 Key，且当前容量已达到上限，直接拒绝！
            if (oldValue == null && map.mappingCount() >= maxCapacity) {
                return null; // 返回 null 时，CHM 底层什么都不会做，完美避免 OOM
            }
            // 容量安全，正常执行传入的业务计算逻辑
            return remappingFunction.apply(k, oldValue);
        });
    }

    /**
     * 包装 computeIfPresent
     * 因为只操作已存在的 Key，一定不会增加容量，所以直接透传，无需拦截。
     */
    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        return map.computeIfPresent(key, remappingFunction);
    }

    /**
     * 包装 computeIfAbsent
     */
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        // 如果容量已满且 Key 不存在，直接返回 null 拒绝执行
        if (map.mappingCount() >= maxCapacity && !map.containsKey(key)) {
            return null;
        }
        return map.computeIfAbsent(key, mappingFunction);
    }

    // ================= 以下是常规 Map 方法的透传 =================

    public V get(K key) {
        return map.get(key);
    }

    public V remove(K key) {
        return map.remove(key);
    }

    public boolean containsKey(K key) {
        return map.containsKey(key);
    }

    /**
     * 返回当前元素的预估数量 (高并发下比 size() 更高效)
     */
    public long mappingCount() {
        return map.mappingCount();
    }

    public void clear() {
        map.clear();
    }
}
