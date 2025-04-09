package cn.net.pap.example.async;

import cn.net.pap.example.async.service.AsyncService;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {cn.net.pap.example.async.Pap4jBoot3ExampleAsyncApplication.class})
public class MultiBeanParallelTest {

    @Autowired
    private AsyncService asyncService;

    // @Test
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
