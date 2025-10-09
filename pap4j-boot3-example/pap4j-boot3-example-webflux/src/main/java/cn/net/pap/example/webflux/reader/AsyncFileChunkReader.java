package cn.net.pap.example.webflux.reader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class AsyncFileChunkReader implements AutoCloseable {

    private final AsynchronousFileChannel channel;

    /**
     * 使用系统默认线程池创建 AsynchronousFileChannel
     */
    public AsyncFileChunkReader(Path filePath) throws IOException {
        Objects.requireNonNull(filePath, "filePath cannot be null");
        this.channel = AsynchronousFileChannel.open(filePath, StandardOpenOption.READ);
    }

    /**
     * 异步读取指定偏移量和长度的文件内容
     */
    public Future<byte[]> readChunk(long offset, int length) {
        ByteBuffer buffer = ByteBuffer.allocate(length);
        CompletableFuture<byte[]> future = new CompletableFuture<>();

        channel.read(buffer, offset, null, new CompletionHandler<Integer, Void>() {
            @Override
            public void completed(Integer bytesRead, Void attachment) {
                buffer.flip();
                byte[] data = new byte[bytesRead];
                buffer.get(data);
                future.complete(data);
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                future.completeExceptionally(exc);
            }
        });

        return future;
    }

    /**
     * 异步读取指定位置的数据块
     */
    public CompletableFuture<byte[]> readChunkAsync(long position, int size) {
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        ByteBuffer buffer = ByteBuffer.allocate(size);

        channel.read(buffer, position, null, new CompletionHandler<Integer, Void>() {
            @Override
            public void completed(Integer bytesRead, Void attachment) {
                if (bytesRead == -1) {
                    future.complete(new byte[0]); // EOF
                    return;
                }
                buffer.flip();
                byte[] chunk = new byte[bytesRead];
                buffer.get(chunk);
                future.complete(chunk);
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                future.completeExceptionally(exc);
            }
        });

        return future;
    }

    /**
     * 同步封装（可选）
     */
    public byte[] readChunkBlocking(long offset, int length) throws IOException {
        try {
            return readChunk(offset, length).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new IOException("Async read failed", e);
        }
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
