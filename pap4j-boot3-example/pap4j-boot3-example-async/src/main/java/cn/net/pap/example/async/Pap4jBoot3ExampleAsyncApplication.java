package cn.net.pap.example.async;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = "cn.net.pap")
@EnableAsync
public class Pap4jBoot3ExampleAsyncApplication {

    public static class Pap4jCustomGenerator implements BeanNameGenerator {
        @Override
        public String generateBeanName(BeanDefinition definition, BeanDefinitionRegistry registry) {
            return definition.getBeanClassName();
        }
    }

    public static void main(String[] args) {
        new SpringApplicationBuilder(Pap4jBoot3ExampleAsyncApplication.class)
                .beanNameGenerator(new Pap4jCustomGenerator())
                .run(args);
    }

}
