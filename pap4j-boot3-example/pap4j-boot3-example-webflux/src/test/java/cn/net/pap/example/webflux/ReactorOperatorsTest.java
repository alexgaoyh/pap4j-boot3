package cn.net.pap.example.webflux;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.function.Tuple2;

import java.time.Duration;

public class ReactorOperatorsTest {

    // ==========================================
    // 1. map 操作符测试
    // ==========================================
    @Test
    public void testMap_SyncTransformation() {
        Flux<String> source = Flux.just("apple", "banana", "cherry");

        // 将每个字符串转换为它的长度 (String -> Integer)
        Flux<Integer> lengthFlux = source.map(fruit -> fruit.length());

        StepVerifier.create(lengthFlux).expectNext(5)  // "apple".length()
                .expectNext(6)  // "banana".length()
                .expectNext(6)  // "cherry".length()
                .verifyComplete(); // 验证流已正常结束
    }

    // ==========================================
    // 2. flatMap 操作符测试
    // ==========================================
    @Test
    public void testFlatMap_AsyncAndFlattening() {
        Flux<String> userIds = Flux.just("user1", "user2");

        // 模拟一个异步的数据库查询方法，返回 Mono。注意：map 会返回 Flux<Mono<String>>，而 flatMap 会将其展平为 Flux<String>
        Flux<String> userDetailsFlux = userIds.flatMap(id -> fakeAsyncDatabaseCall(id));

        StepVerifier.create(userDetailsFlux).expectNext("Details_for_user1").expectNext("Details_for_user2").verifyComplete();
    }

    @Test
    public void testFlatMap_ConcurrencyAndInterleaving() {
        // flatMap 是并发的，不保证顺序！(如果需要保证顺序，应该使用 concatMap)
        Flux<String> letters = Flux.just("A", "B", "C");

        Flux<String> concurrentFlux = letters.flatMap(letter -> {
            // A 延迟 300ms，B 延迟 100ms，C 延迟 200ms
            int delay = letter.equals("A") ? 300 : (letter.equals("B") ? 100 : 200);
            return Mono.just(letter + "-processed").delayElement(Duration.ofMillis(delay));
        });

        // 验证结果：最先完成的 B 先出来，然后是 C，最后是 A
        StepVerifier.create(concurrentFlux).expectNext("B-processed").expectNext("C-processed").expectNext("A-processed").verifyComplete();
    }

    // ==========================================
    // 3. zip 操作符测试
    // ==========================================
    @Test
    public void testZip_CombineTwoStreams() {
        Flux<String> letters = Flux.just("A", "B", "C");
        Flux<Integer> numbers = Flux.just(1, 2, 3, 4); // 注意：这里多了一个 4

        // zip 默认将元素打包为 Tuple2 (元组)
        Flux<Tuple2<String, Integer>> zippedFlux = Flux.zip(letters, numbers);

        StepVerifier.create(zippedFlux).expectNextMatches(tuple -> tuple.getT1().equals("A") && tuple.getT2() == 1).expectNextMatches(tuple -> tuple.getT1().equals("B") && tuple.getT2() == 2).expectNextMatches(tuple -> tuple.getT1().equals("C") && tuple.getT2() == 3)
                // 因为 letters 只有 3 个元素，所以 numbers 的 4 会被忽略，流直接结束。这就是拉链的"木桶效应"。
                .verifyComplete();
    }

    @Test
    public void testZip_WithCustomCombinator() {
        Mono<String> firstName = Mono.just("Bruce");
        Mono<String> lastName = Mono.just("Wayne").delayElement(Duration.ofMillis(100));

        // 自定义组合逻辑，不用默认的 Tuple
        Mono<String> fullName = Mono.zip(firstName, lastName, (first, last) -> first + " " + last);

        StepVerifier.create(fullName).expectNext("Bruce Wayne") // 会等待最慢的 lastName 准备好
                .verifyComplete();
    }

    // ==========================================
    // 4. switchIfEmpty 操作符测试
    // ==========================================
    @Test
    public void testSwitchIfEmpty_WhenSourceIsEmpty() {
        Mono<String> emptySource = Mono.empty();

        // 当主数据流为空时，切换到备胎数据流
        Mono<String> result = emptySource.switchIfEmpty(Mono.just("Fallback_Data"));

        StepVerifier.create(result).expectNext("Fallback_Data").verifyComplete();
    }

    @Test
    public void testSwitchIfEmpty_WhenSourceHasData() {
        Mono<String> validSource = Mono.just("Valid_Data");

        // 当主数据流有数据时，备胎流根本不会发射数据
        Mono<String> result = validSource.switchIfEmpty(Mono.just("Fallback_Data"));

        StepVerifier.create(result).expectNext("Valid_Data").verifyComplete();
    }

    @Test
    public void testSwitchIfEmpty_TheDeferGotcha() {
        Mono<String> cacheData = Mono.just("Data_From_Cache");

        // 使用 Mono.defer() 进行延迟执行 (Lazy evaluation)，只有当真正触发 switchIfEmpty 时，里面的逻辑才会被执行。
        Mono<String> result = cacheData.switchIfEmpty(Mono.defer(() -> heavyDatabaseCall()));

        StepVerifier.create(result).expectNext("Data_From_Cache").verifyComplete();
    }

    // ==========================================
    // 辅助模拟方法
    // ==========================================
    private Mono<String> fakeAsyncDatabaseCall(String id) {
        return Mono.just("Details_for_" + id);
    }

    private Mono<String> heavyDatabaseCall() {
        return Mono.just("Data_From_DB");
    }
}
