package cn.net.pap.example.doris;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication(scanBasePackages = "cn.net.pap")
@MapperScan("cn.net.pap")
public class Pap4jBoot3ExampleDorisApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(Pap4jBoot3ExampleDorisApplication.class)
                .run(args);
    }

}
