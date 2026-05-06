package cn.net.pap.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = "cn.net.pap")
@EnableJpaAuditing
@EnableAsync
public class Pap4jBoot3ExampleWebhookApplication {

    private static final Logger log = LoggerFactory.getLogger(Pap4jBoot3ExampleWebhookApplication.class);

    public static class Pap4jCustomGenerator implements BeanNameGenerator {
        @Override
        public String generateBeanName(BeanDefinition definition, BeanDefinitionRegistry registry) {
            return definition.getBeanClassName();
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(Pap4jBoot3ExampleWebhookApplication.class)
                .beanNameGenerator(new Pap4jCustomGenerator())
                .run(args);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (context != null && context.isActive()) {
                int code = SpringApplication.exit(context);
                log.info("Web应用优雅关闭，退出码: {}", code);
            }
        }));
    }

}
