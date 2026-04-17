package cn.net.pap.example.webflux;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ZeroCopySliceTest {

    private static final Logger log = LoggerFactory.getLogger(ZeroCopySliceTest.class);

    @Test
    public void testNettyZeroCopySlicing() {
        // 1. 分配堆外内存
        ByteBuf originalBuf = PooledByteBufAllocator.DEFAULT.directBuffer(100);

        try {
            originalBuf.writeBytes("Hello-Zero-Copy".getBytes(StandardCharsets.UTF_8));
            assertEquals(1, originalBuf.refCnt(), "初始分配的 ByteBuf 引用计数必须为 1");

            // 2. 创建切片 (现代 Netty 会返回拥有独立计数器的视图)
            ByteBuf sliceForTaskA = originalBuf.retainedSlice();
            ByteBuf sliceForTaskB = originalBuf.retainedSlice();

            assertEquals(3, originalBuf.refCnt(), "原 Buf 的计数器为 3");
            assertEquals(1, sliceForTaskA.refCnt(), "切片 A 拥有独立的计数器，初始为 1");
            assertEquals(1, sliceForTaskB.refCnt(), "切片 B 拥有独立的计数器，初始为 1");

            // 3. 零拷贝铁证：修改 A，B 跟着变 (底层物理内存是同一块)
            sliceForTaskA.setByte(0, (byte) 'J');
            assertEquals((byte) 'J', sliceForTaskB.getByte(0), "TaskB 读到的数据受到了 TaskA 修改的影响！");

            // 4. 模拟释放
            sliceForTaskA.release();
            assertEquals(0, sliceForTaskA.refCnt(), "TaskA 释放完毕，其独立计数器归零");

            sliceForTaskB.release();
            assertEquals(0, sliceForTaskB.refCnt(), "TaskB 释放完毕，其独立计数器归零");

            // 此时原 Buf 依然存活，等待网关主线程释放
            assertEquals(1, originalBuf.refCnt(), "下游任务释放完毕，不影响原 Buf 的存活状态");

        } catch (Throwable t) {
            log.error("", t);
        } finally {
            originalBuf.release();
            assertEquals(0, originalBuf.refCnt(), "网关主线程释放完毕，原 Buf 计数归零。底层物理内存被彻底回收！");
        }
    }

}