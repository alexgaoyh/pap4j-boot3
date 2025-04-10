
将 druid 下的 stat 部分配合 logback 持久化.

```xml
    <!-- POST http://127.0.0.1:6060/druid/log-and-reset.json {"ResultCode": 1,"Content": null} -->
    <appender name="arc-druid" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${log.path}/arc-druid.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>${log.path}/arc-druid.%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>60</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>${log.pattern}</pattern>
        </encoder>
    </appender>

    <logger name="com.alibaba.druid.pool.DruidDataSourceStatLoggerImpl" level="DEBUG" additivity="false">
        <appender-ref ref="arc-druid"/>
    </logger>

```