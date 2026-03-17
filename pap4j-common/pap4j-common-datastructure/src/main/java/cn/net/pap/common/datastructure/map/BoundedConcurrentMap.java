package cn.net.pap.common.datastructure.map;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * <p><strong>BoundedConcurrentMap</strong> 是一个受防内存溢出（OOM）保护的并发映射（Concurrent Map）。</p>
 *
 * <p>它采用“软边界（Soft Bound）”机制，在高并发期间保护内存，而不会产生全局锁的开销。
 * 它封装了一个 {@link ConcurrentHashMap} 并限制了其容量。</p>
 *
 * <ul>
 *     <li>为映射数量提供上限。</li>
 *     <li>提供模仿标准映射行为的线程安全操作。</li>
 * </ul>
 *
 * @param <K> 此映射维护的键的类型
 * @param <V> 映射值的类型
 */
public class BoundedConcurrentMap<K, V> {

    // 底层依然使用高性能的 ConcurrentHashMap
    private final ConcurrentHashMap<K, V> map;
    // 最大容量阈值
    private final long maxCapacity;

    /**
     * <p>构造一个具有指定容量限制的新的 <strong>BoundedConcurrentMap</strong>。</p>
     *
     * @param maxCapacity 允许的最大容量。
     * @throws IllegalArgumentException 如果 maxCapacity 小于或等于 0。
     */
    public BoundedConcurrentMap(long maxCapacity) {
        if (maxCapacity <= 0) {
            throw new IllegalArgumentException("最大容量必须大于 0");
        }
        this.map = new ConcurrentHashMap<>();
        this.maxCapacity = maxCapacity;
    }

    /**
     * <p>尝试安全地放入一个键值对。</p>
     *
     * <p>在插入之前会进行容量检查。高并发可能导致容量稍微超出限制（软边界），但它能有效防止 OOM。</p>
     *
     * @param key   键。
     * @param value 值。
     * @return <strong>true</strong> 如果放入或更新成功；<strong>false</strong> 如果因容量限制被拒绝。
     * @throws NullPointerException 如果键或值为 null。
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
     * <p>包装原生的 <code>compute</code> 方法以注入容量拦截逻辑。</p>
     *
     * @param key               要计算的键。
     * @param remappingFunction 用于计算值的函数。
     * @return 计算出的值，如果执行被拒绝则返回 null。
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
     * <p>包装原生的 <code>computeIfPresent</code> 方法。</p>
     *
     * <p>因为它仅对现有的键进行操作，所以直接委托执行。</p>
     *
     * @param key               键。
     * @param remappingFunction 计算函数。
     * @return 计算出的值，或 null。
     */
    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        return map.computeIfPresent(key, remappingFunction);
    }

    /**
     * <p>包装原生的 <code>computeIfAbsent</code> 方法。</p>
     *
     * <p>如果映射已达到满容量并且该键不存在，则拦截操作。</p>
     *
     * @param key             键。
     * @param mappingFunction 映射函数。
     * @return 计算出的或已存在的值，如果被拒绝则返回 null。
     */
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        // 如果容量已满且 Key 不存在，直接返回 null 拒绝执行
        if (map.mappingCount() >= maxCapacity && !map.containsKey(key)) {
            return null;
        }
        return map.computeIfAbsent(key, mappingFunction);
    }

    /**
     * <p>检索与键关联的值。</p>
     *
     * @param key 键。
     * @return 映射的值，如果不存在则返回 null。
     */
    public V get(K key) {
        return map.get(key);
    }

    /**
     * <p>从此映射中移除某个键的映射关系。</p>
     *
     * @param key 键。
     * @return 与键关联的先前的值。
     */
    public V remove(K key) {
        return map.remove(key);
    }

    /**
     * <p>检查映射是否包含给定的键。</p>
     *
     * @param key 键。
     * @return <strong>true</strong> 如果映射包含该键。
     */
    public boolean containsKey(K key) {
        return map.containsKey(key);
    }

    /**
     * <p>返回映射数量的估计值。</p>
     *
     * @return 映射的数量。
     */
    public long mappingCount() {
        return map.mappingCount();
    }

    /**
     * <p>从映射中清除所有条目。</p>
     */
    public void clear() {
        map.clear();
    }
}
