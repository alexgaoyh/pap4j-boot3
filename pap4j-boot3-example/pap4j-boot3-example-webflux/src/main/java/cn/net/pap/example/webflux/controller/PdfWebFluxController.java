package cn.net.pap.example.webflux.controller;

import cn.net.pap.example.webflux.reader.AsyncFileChunkReader;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/pdf")
public class PdfWebFluxController {

    private static final int DEFAULT_CHUNK_SIZE = 10 * 1024 * 1024; // 10MB 分片

    @GetMapping("/view/{fileName}")
    public Mono<ResponseEntity<Flux<DataBuffer>>> streamPdf(@PathVariable String fileName, ServerWebExchange exchange) throws IOException {

        Path filePath = Path.of("d:/", fileName);

        if (!Files.exists(filePath)) {
            return Mono.just(ResponseEntity.notFound().build());
        }

        long fileSize = Files.size(filePath);

        String rangeHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.RANGE);

        long start = 0;
        long end = fileSize - 1;
        boolean isRangeRequest = false;

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            isRangeRequest = true;
            String[] parts = rangeHeader.replace("bytes=", "").split("-");
            try {
                start = Long.parseLong(parts[0]);
                if (parts.length > 1 && !parts[1].isEmpty()) {
                    end = Long.parseLong(parts[1]);
                }
            } catch (NumberFormatException e) {
                return Mono.just(ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE).build());
            }
        }

        final long finalStart = start;
        final long finalEnd = end;

        AsyncFileChunkReader reader = new AsyncFileChunkReader(filePath);

        Flux<DataBuffer> body = Flux.create(sink -> {
            readChunkSequentially(reader, sink, finalStart, finalEnd);
        }, FluxSink.OverflowStrategy.BUFFER);

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE);
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.set(HttpHeaders.CONTENT_LENGTH, String.valueOf(finalEnd - finalStart + 1));

        if (isRangeRequest) {
            headers.set(HttpHeaders.CONTENT_RANGE, String.format("bytes %d-%d/%d", finalStart, finalEnd, fileSize));
            return Mono.just(ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).headers(headers).body(body));
        } else {
            return Mono.just(ResponseEntity.ok().headers(headers).body(body));
        }
    }

    private void readChunkSequentially(AsyncFileChunkReader reader, FluxSink<DataBuffer> sink, long offset, long end) {
        if (offset > end) {
            try {
                reader.close();
            } catch (IOException ignored) {
            }
            sink.complete();
            return;
        }

        int chunkSize = (int) Math.min(DEFAULT_CHUNK_SIZE, end - offset + 1);

        reader.readChunkAsync(offset, chunkSize).whenComplete((chunk, err) -> {
            if (err != null) {
                sink.error(err);
                return;
            }
            if (chunk.length > 0) {
                sink.next(new DefaultDataBufferFactory().wrap(chunk));
            }
            // 读取下一个 chunk
            readChunkSequentially(reader, sink, offset + chunk.length, end);
        });
    }


}

