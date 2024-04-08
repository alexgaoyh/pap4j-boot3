package cn.net.pap.example.assembly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "cn.net.pap.example.assembly")
public class Pap4jBoot3ExampleAssemblyApplication {

    public static void main(String[] args) {
        SpringApplication.run(Pap4jBoot3ExampleAssemblyApplication.class, args);
    }

}
