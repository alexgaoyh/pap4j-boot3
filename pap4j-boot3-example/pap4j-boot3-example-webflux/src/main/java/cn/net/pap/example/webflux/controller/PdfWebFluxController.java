package cn.net.pap.example.webflux.controller;

import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Path;

@RestController
@RequestMapping("/pdf")
public class PdfWebFluxController {

    @GetMapping("/view/{fileName}")
    public Mono<ResponseEntity<Resource>> streamPdf(@PathVariable String fileName) {
        // 1. 定义基础目录
        Path basePath = Path.of(System.getProperty("java.io.tmpdir")).normalize();

        // 2. 解析目标文件路径并规范化
        Path filePath = basePath.resolve(fileName).normalize();

        // 3. 安全校验：确保请求的文件确实在 basePath 目录下，防止路径遍历攻击
        if (!filePath.startsWith(basePath)) {
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
        }

        Resource resource = new PathResource(filePath);

        if (!resource.exists() || !resource.isReadable()) {
            return Mono.just(ResponseEntity.notFound().build());
        }

        return Mono.just(ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .body(resource));
    }

    @GetMapping("/view2/{fileName}")
    public Mono<ResponseEntity<?>> streamPdf2(@PathVariable String fileName) {
        return Mono.fromCallable(() -> {
            Path basePath = Path.of(System.getProperty("java.io.tmpdir")).normalize();
            // 1. 防御：防止文件名包含 .. 尝试越权
            Path filePath = basePath.resolve(fileName).normalize();

            if (!filePath.startsWith(basePath)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).<Resource>build();
            }

            Resource resource = new PathResource(filePath);
            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            // 2. 构建响应，这里可以增加 HttpHeaders.CACHE_CONTROL，用来允许浏览器缓存
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(resource);
        }).subscribeOn(Schedulers.boundedElastic()); // 确保 I/O 在独立线程池运行
    }


}

