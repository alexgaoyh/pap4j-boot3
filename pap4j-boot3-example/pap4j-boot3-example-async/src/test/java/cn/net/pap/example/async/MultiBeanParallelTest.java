package cn.net.pap.example.async;

import cn.net.pap.example.async.service.AsyncService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestConstructor;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@SpringBootTest(classes = {cn.net.pap.example.async.Pap4jBoot3ExampleAsyncApplication.class})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
public class MultiBeanParallelTest {

    private final AsyncService asyncService;

    public MultiBeanParallelTest(AsyncService asyncService) {
        this.asyncService = asyncService;
    }

    @Test
    public void parallelTest() throws Exception {
        List<Supplier<String>> tasks = Arrays.asList(
                () -> asyncService.method1("alexgaoyh"),
                () -> asyncService.method2("alexgaoyh"),
                () -> asyncService.method3("alexgaoyh")
        );

        long start = System.currentTimeMillis();
        List<String> collect = tasks.parallelStream().map(Supplier::get).collect(Collectors.toList());
        long end = System.currentTimeMillis();
        System.out.println(end - start);
        System.out.println(collect);

    }

}
