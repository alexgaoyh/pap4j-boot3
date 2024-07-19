
自定义 Cacheable 注解，增加 fields 参数，可以配置额外的缓存数据，比如将 Map List 同样存入 Redis 中.

1、引入 POM

```xml
    <dependency>
        <groupId>cn.net.pap</groupId>
        <artifactId>pap4j-boot3-starters-cache</artifactId>
        <version>0.0.1</version>
    </dependency>
```

2、配置文件调整

```properties
    spring.data.redis.host=127.0.0.1
    spring.data.redis.port=6379
    spring.data.redis.password=
```

3、指定方法增加注解

```html
    @CacheableField(value = "Proguard", key = "'Proguard:id:'+#proguardId", fields = {
            @CacheableType(field = "extMap", type = "map"),
            @CacheableType(field = "extList", type = "list")
    })
    public Proguard getProguardByProguardId(Long proguardId) {
        
    }
```

4、单元测试
```html
    @Test
    public void get() {
        Object threadId = redisTemplate.opsForValue().get("Proguard::Proguard:id:1721376543523:extMap:threadId");
        Object timeswap = redisTemplate.opsForValue().get("Proguard::Proguard:id:1721376543523:extMap:timeswap");
        Object extList = redisTemplate.opsForValue().get("Proguard::Proguard:id:1721376543523:extList");
        assertTrue(threadId instanceof String);
        assertTrue(timeswap instanceof Long);
        assertTrue(extList instanceof ArrayList);
        System.out.println(threadId);
        System.out.println(timeswap);
        System.out.println(extList);
    }
```





