package cn.net.pap.example.proguard;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication(scanBasePackages = "cn.net.pap")
public class Pap4jBoot3ExampleProguardApplication {

    public static class Pap4jCustomGenerator implements BeanNameGenerator {
        @Override
        public String generateBeanName(BeanDefinition definition, BeanDefinitionRegistry registry) {
            return definition.getBeanClassName();
        }
    }

    public static void main(String[] args) {
        new SpringApplicationBuilder(Pap4jBoot3ExampleProguardApplication.class)
                .beanNameGenerator(new Pap4jCustomGenerator())
                .run(args);
    }

}
