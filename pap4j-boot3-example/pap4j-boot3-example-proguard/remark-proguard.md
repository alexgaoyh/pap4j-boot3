# 使用 proguard 进行混淆

## 背景
&ensp;&ensp;对 spring boot 进行混淆。

## 示例一
&ensp;&ensp;对于 entity repository service controller config 相关的 Bean ，不进行混淆，其他部分进行混淆。

```xml

<build>
    <plugins>
        <plugin>
            <groupId>com.github.wvengen</groupId>
            <artifactId>proguard-maven-plugin</artifactId>
            <version>2.3.1</version>
            <executions>
                <execution>
                    <phase>package</phase>
                    <goals>
                        <goal>proguard</goal>
                    </goals>
                </execution>
            </executions>
            <configuration>
                <injar>${project.build.finalName}.jar</injar>
                <outjar>${project.build.finalName}.jar</outjar>
                <obfuscate>true</obfuscate>
                <proguardInclude>${project.basedir}/proguard-rules.pro</proguardInclude>
                <outputDirectory>${project.basedir}/target</outputDirectory>
            </configuration>
        </plugin>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
            <configuration>
                <mainClass>cn.net.pap.example.Pap4jBjczyVoteApplication</mainClass>
                <classifier>exec</classifier>
            </configuration>
            <executions>
                <execution>
                    <id>repackage</id>
                    <phase>package</phase>
                    <goals>
                        <goal>repackage</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>

```

```properties
# ---------------------------------------------------------------
# 基本配置
# ---------------------------------------------------------------
# 禁用优化和收缩（根据你的需求保留）
-dontoptimize
-dontshrink

# 启用混淆（删除或注释掉 -dontobfuscate）
# -dontobfuscate

# 禁用警告（谨慎使用）
-dontwarn

# 保留Spring Boot相关注解
-keepattributes *Annotation*,Exceptions,Signature,InnerClasses,EnclosingMethod

# 保留Spring Boot自动配置
-keep class org.springframework.boot.autoconfigure.** { *; }
-keep class org.springframework.boot.web.** { *; }

# 保留ServletWebServerFactory实现
-keep public class * implements org.springframework.boot.web.servlet.server.ServletWebServerFactory { *; }

# 保留Spring组件
-keep @org.springframework.stereotype.Component public class * { *; }
-keep @org.springframework.stereotype.Service public class * { *; }
-keep @org.springframework.stereotype.Repository public class * { *; }
-keep @org.springframework.stereotype.Controller public class * { *; }

# 保留Spring Boot应用程序类
-keep @org.springframework.boot.autoconfigure.SpringBootApplication public class * { *; }

# 保留配置属性类
-keep @org.springframework.boot.context.properties.ConfigurationProperties public class * { *; }
-keep @org.springframework.boot.context.properties.EnableConfigurationProperties public class * { *; }

# ---------------------------------------------------------------
# Spring Boot 相关保留规则
# ---------------------------------------------------------------
# 保留 Spring Boot 启动类
-keep class cn.net.pap.example.Pap4jExampleApplication {
    public static void main(java.lang.String[]);
}

# 保留 Spring 相关注解和类
-keep class org.springframework.** { *; }
-keepclassmembers class * {
    @org.springframework.beans.factory.annotation.Autowired *;
}

# 保留多个包下的所有类
-keep class cn.net.pap.example.config.** { *; }
-keep class cn.net.pap.example.controller.** { *; }
-keep class cn.net.pap.example.entity.** { *; }
-keep class cn.net.pap.example.repository.** { *; }
-keep class cn.net.pap.example.service.** { *; }

# ---------------------------------------------------------------
# SaltUtil 类的特殊配置
# ---------------------------------------------------------------
# 允许混淆类名（但保留类本身不被删除）
-keep class cn.net.pap.example.util.SaltUtil

# 保持所有方法名（包括静态方法）不被混淆，但允许优化和局部变量混淆
-keepclassmembers,allowoptimization class cn.net.pap.example.util.SaltUtil {
    *;  # 匹配所有成员（方法名、字段名不混淆）
}

# 确保所有方法不被删除（即使未使用）
-keepclassmembers class cn.net.pap.example.util.SaltUtil {
    <methods>;  # 防止方法被移除
}

# ---------------------------------------------------------------
# 其他通用规则
# ---------------------------------------------------------------
# 保留序列化相关成员
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
}

# 允许调整访问修饰符（避免某些反射问题）
-allowaccessmodification
```
