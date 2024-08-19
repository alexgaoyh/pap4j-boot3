
自定义 Cacheable 注解(CacheableFuzzyField/CacheEvictFuzzyField) ，增加 singleFuzzyField 参数，可以在缓存的时候指定某字段进行模糊搜索。

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
    spring.data.redis.database=8
```

3、指定方法增加注解

```html
    @cn.net.pap.cache.annotation.CacheableFuzzyField(value = "Proguard",
        key = "'Proguard:id:'+#proguardId",
        singleFuzzyField = "proguardName")
    @Override
    public Proguard getProguardByProguardId(Long proguardId) {
        return proguardRepository.getProguardByProguardId(proguardId);
    }

    @cn.net.pap.cache.annotation.CacheEvictFuzzyField(value = "Proguard",
        key = "'Proguard:id:'+#proguardId",
        singleFuzzyField = "proguardName")
    @Override
    public void deleteAllById(Long proguardId) {
        proguardRepository.deleteById(proguardId);
    }
```

4、单元测试
```html
    @Test
    public void crudTest2() {
        Map<String, Object> extMap = new HashMap<>();
        extMap.put("timeswap", System.currentTimeMillis());
        List<String> extList = new ArrayList<>();
        extList.add("A");
    
        Proguard proguard1 = new Proguard();
        proguard1.setProguardId(1l);
        proguard1.setProguardName("alexgaoyh");
        proguard1.setExtMap(extMap);
        proguard1.setExtList(extList);
        proguardService.saveAndFlush(proguard1);
    
        Proguard proguardByProguardId = proguardService.getProguardByProguardId(1l);
    
        System.out.println(proguardByProguardId);
    
        proguardService.deleteAllById(1l);
    
        System.out.println("deleteAllById");
    }

```





