package cn.net.pap.example.proguard.config;

import cn.net.pap.example.proguard.entity.Proguard;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;

/**
 * Jackson 序列化预编译与监控配置类
 * <p>
 * 主要功能：
 * 1. 预编译常用类型的序列化器，提升首次序列化性能
 * 2. 通过装饰器模式包装所有 Bean 序列化器，添加性能监控
 * 3. 监控并输出每个对象的序列化耗时，用于性能分析和优化
 * <p>
 * 实现机制：
 * - 使用 CommandLineRunner 在应用启动时预编译 序列化器
 * - 通过自定义 BeanSerializerModifier 拦截所有 Bean 序列化器
 * - 采用 MonitoringWrapperSerializer 装饰器包装原始序列化器，添加计时逻辑
 * - 监控信息以 [JacksonMonitor] 为前缀输出到日志，包含类型名称和耗时(ms)
 * <p>
 * 监控范围：所有 Bean 类型的序列化操作，排除基础类型和集合类型
 *
 * @see MonitoringSerializerModifier 序列化器修改器，用于包装 Bean 序列化器
 * @see MonitoringWrapperSerializer 监控装饰器，负责性能计时和日志输出
 */
@Configuration
public class JacksonPrecompileConfig {

    @Bean
    CommandLineRunner precompileWriters(ObjectMapper mapper) {
        return args -> {
            mapper.writerFor(new TypeReference<ResponseEntity<Proguard>>() {
            });
            mapper.writerFor(Proguard.class);
            SimpleModule monitorModule = new SimpleModule();
            monitorModule.setSerializerModifier(new MonitoringSerializerModifier());
            mapper.registerModule(monitorModule);
        };
    }

    static class MonitoringSerializerModifier extends BeanSerializerModifier {
        @Override
        public JsonSerializer<?> modifySerializer(SerializationConfig config, BeanDescription beanDesc, JsonSerializer<?> serializer) {

            // 只包装 Bean 类型
            if (serializer != null && !(serializer instanceof MonitoringWrapperSerializer)) {
                return new MonitoringWrapperSerializer<>(serializer);
            }
            return serializer;
        }
    }

    /**
     * 装饰器模式：包装原始 serializer，加入监控逻辑
     */
    static class MonitoringWrapperSerializer<T> extends JsonSerializer<T> {
        private final JsonSerializer<T> delegate;

        MonitoringWrapperSerializer(JsonSerializer<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void serialize(T value, JsonGenerator gen, SerializerProvider serializers) throws java.io.IOException {
            long start = System.nanoTime();
            delegate.serialize(value, gen, serializers);
            long duration = System.nanoTime() - start;

            System.out.printf("[JacksonMonitor] Serialized %s in %.3f ms%n", value != null ? value.getClass().getSimpleName() : "null", duration / 1_000_000.0);
        }

        @Override
        public boolean isEmpty(SerializerProvider provider, T value) {
            return delegate.isEmpty(provider, value);
        }
    }

}
