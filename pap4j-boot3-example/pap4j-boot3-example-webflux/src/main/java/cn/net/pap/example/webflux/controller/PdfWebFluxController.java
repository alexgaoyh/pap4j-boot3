package cn.net.pap.example.webflux.controller;

import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.nio.file.Path;

@RestController
@RequestMapping("/pdf")
public class PdfWebFluxController {

    @GetMapping("/view/{fileName}")
    public Mono<ResponseEntity<Resource>> streamPdf(@PathVariable String fileName) {
        // 1. 定义基础目录
        Path basePath = Path.of("d:/").normalize();

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


}

