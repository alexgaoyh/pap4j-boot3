package cn.net.pap.example.apitester.controller;

import cn.net.pap.example.apitester.dto.MergeRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;

@RestController
@RequestMapping("/upload")
@Tag(name = "分片上传接口", description = "提供非阻塞 WebFlux 响应式的分片上传、文件合并与分片清理接口")
public class ChunkUploadController {

    private final Path uploadDir = Paths.get(System.getProperty("java.io.tmpdir"), "temp");

    public ChunkUploadController() throws Exception {
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }
    }

    /**
     * 分片上传接口 - 流式写入
     * 前端需传 headers: X-Upload-Id, X-Chunk-Index
     */
    @Operation(summary = "上传分片 (流式写入)", description = "接收一个分片的数据流，并根据请求头中的 X-Upload-Id 和 X-Chunk-Index 写入指定的临时分片文件中。")
    @PostMapping
    public Mono<ResponseEntity<String>> uploadChunk(ServerHttpRequest request,
                                                    @RequestBody Flux<DataBuffer> body) {
        String uploadId = request.getHeaders().getFirst("X-Upload-Id");
        String chunkIndexStr = request.getHeaders().getFirst("X-Chunk-Index");

        if (uploadId == null || chunkIndexStr == null) {
            return Mono.just(ResponseEntity.badRequest().body("Missing headers: X-Upload-Id or X-Chunk-Index"));
        }

        int chunkIndex;
        try {
            chunkIndex = Integer.parseInt(chunkIndexStr);
        } catch (NumberFormatException e) {
            return Mono.just(ResponseEntity.badRequest().body("Invalid X-Chunk-Index"));
        }

        Path chunkPath = uploadDir.resolve(uploadId + "_" + chunkIndex + ".part");

        return DataBufferUtils.write(body, chunkPath, StandardOpenOption.CREATE)
                .publishOn(Schedulers.boundedElastic())
                .map(db -> ResponseEntity.ok("Chunk " + chunkIndex + " uploaded for uploadId: " + uploadId))
                .onErrorResume(e -> Mono.just(ResponseEntity.status(500)
                        .body("Failed to upload chunk: " + e.getMessage())));
    }

    /**
     * 合并 chunk 文件
     * 前端需传 JSON: { uploadId, fileName, totalChunks }
     */
    @Operation(summary = "合并已上传的分片文件")
    @PostMapping("/merge")
    public Mono<ResponseEntity<String>> mergeChunks(@RequestBody MergeRequest request) {
        String fileName = request.getFileName();
        String uploadId = request.getUploadId();
        int totalChunks = request.getTotalChunks();

        if (fileName == null || uploadId == null || totalChunks <= 0) {
            return Mono.just(ResponseEntity.badRequest().body("Invalid merge request"));
        }

        Path finalFile = uploadDir.resolve(fileName);

        return Mono.fromCallable(() -> {
                    try (FileChannel out = FileChannel.open(finalFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                        for (int i = 0; i < totalChunks; i++) {
                            Path chunkFile = uploadDir.resolve(uploadId + "_" + i + ".part");
                            if (!Files.exists(chunkFile)) {
                                throw new IllegalStateException("Missing chunk: " + chunkFile.getFileName());
                            }
                            try (FileChannel in = FileChannel.open(chunkFile, StandardOpenOption.READ)) {
                                long pos = 0;
                                long size = in.size();
                                while (pos < size) {
                                    pos += in.transferTo(pos, size - pos, out);
                                }
                            }
                        }
                    }

                    // 异步删除分片文件
                    Mono.fromRunnable(() -> {
                        for (int i = 0; i < totalChunks; i++) {
                            try {
                                Files.deleteIfExists(uploadDir.resolve(uploadId + "_" + i + ".part"));
                            } catch (IOException ignored) {}
                        }
                    }).subscribeOn(Schedulers.boundedElastic()).subscribe();

                    return ResponseEntity.ok("File merged successfully: " + finalFile.getFileName());
                }).subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> Mono.just(ResponseEntity.status(500)
                        .body("Failed to merge file: " + e.getMessage())));
    }

    /**
     * 可选：清理某个 uploadId 的所有分片（用于异常中断恢复）
     */
    @Operation(summary = "清理某个 Upload ID 的所有分片文件")
    @DeleteMapping("/cleanup/{uploadId}")
    public Mono<ResponseEntity<String>> cleanupChunks(@Parameter(description = "上传 ID") @PathVariable String uploadId) {
        return Mono.fromCallable(() -> {
            try (var files = Files.list(uploadDir)) {
                files.filter(f -> f.getFileName().toString().startsWith(uploadId + "_"))
                        .sorted(Comparator.reverseOrder())
                        .forEach(f -> {
                            try {
                                Files.delete(f);
                            } catch (Exception ignored) {
                            }
                        });
            }
            return ResponseEntity.ok("Cleanup done for uploadId: " + uploadId);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Operation(summary = "直接多部分表单文件上传")
    @PostMapping("/direct")
    public Mono<ResponseEntity<String>> uploadDirect(ServerWebExchange exchange) {
        return exchange.getMultipartData()
                .flatMap(map -> {
                    // map.getFirst("file") 返回的是 Part，需要强转为 FilePart
                    org.springframework.http.codec.multipart.Part part = map.getFirst("file");
                    if (!(part instanceof org.springframework.http.codec.multipart.FilePart)) {
                        return Mono.just(ResponseEntity.badRequest().body("No file uploaded"));
                    }

                    org.springframework.http.codec.multipart.FilePart filePart =
                            (org.springframework.http.codec.multipart.FilePart) part;

                    // 正确获取上传文件名
                    Path dest = uploadDir.resolve(filePart.filename());

                    // 使用 transferTo 自动写入磁盘
                    return filePart.transferTo(dest)
                            .then(Mono.just(ResponseEntity.ok("File uploaded: " + dest.getFileName())))
                            .onErrorResume(e -> Mono.just(ResponseEntity.status(500)
                                    .body("Upload failed: " + e.getMessage())));
                });
    }


}
