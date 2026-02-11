package cn.net.pap.example.proguard.controller;

import cn.net.pap.example.proguard.entity.Proguard;
import cn.net.pap.example.proguard.properties.DemoProperties;
import cn.net.pap.example.proguard.service.IProguardService;
import cn.net.pap.example.proguard.util.SimpleRateLimiter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
public class ProguardController {

    @Autowired
    private IProguardService proguardService;

    @Autowired
    private DemoProperties demoProperties;

    @Autowired
    private Map<String, Class<?>> entityMappings;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @RequestMapping("/first")
    public String first(HttpServletResponse resp) throws IOException {
        Cookie userCookie = new Cookie("username", "alexgaoyh");
        userCookie.setMaxAge(24 * 60 * 60); // 1天
        userCookie.setPath("/");
        Cookie tokenCookie = new Cookie("token", "pap.net.cn");
        tokenCookie.setHttpOnly(true);
        tokenCookie.setMaxAge(10); // 30分钟
        tokenCookie.setPath("/");
        resp.addCookie(userCookie);
        resp.addCookie(tokenCookie);
        return "{\"code\" : \"success\"}";
    }

    @RequestMapping("/second")
    public String second(HttpServletRequest request) {
        String resultStr = "";
        Cookie[] cookies = request.getCookies();
        if(cookies != null) {
            for(Cookie cookie : cookies) {
                resultStr = resultStr + cookie.getName().toString() + " : " + cookie.getValue().toString() + " ; ";
            }
        }
        return "{\"code\" : \""+resultStr+"\"}";
    }

    @GetMapping(value = "/demoProperties", produces = "application/json;charset=UTF-8")
    public DemoProperties demoProperties() {
        return demoProperties;
    }

    @GetMapping(value = "/userDir", produces = "application/json;charset=UTF-8")
    public String userDir() {
        return Path.of(System.getProperty("user.dir")).toFile().getAbsolutePath();
    }

    @GetMapping(value = "/batch", produces = "application/json;charset=UTF-8")
    public String batch() {
        try {
            if(SimpleRateLimiter.tryAcquire("123") <= 1) {
                System.out.println("1");
                Thread.sleep(1000);
            }
        } catch (Exception e) {

        } finally {
            SimpleRateLimiter.release("123");
        }
        return "pap.net.cn!";
    }

    /**
     * Cache Test Interface .
     * @return
     */
    @GetMapping(value = "/print", produces = "application/json;charset=UTF-8")
    public String print() {
        System.out.println(new Date().toString());
        return "pap.net.cn!";
    }

    /**
     * Set Cache-Control in response
     * @param request
     * @param response
     * @return
     */
    @GetMapping("cacheControlTest")
    public String cacheControlTest(HttpServletRequest request, HttpServletResponse response) {
        String papCacheHeaderValue = request.getHeader("Pap-Cache-Header");
        if (!StringUtils.isEmpty(papCacheHeaderValue)) {
            response.setHeader("Cache-Control", CacheControl.maxAge(Duration.ofMinutes(1)).mustRevalidate().getHeaderValue());
        }
        return "success";
    }

    /**
     * LAST_MODIFIED 响应头处理
     * @return
     */
    @GetMapping("/lastModifiedTest")
    public ResponseEntity<String> lastModifiedTest() {

        if (new Random().nextBoolean()) {
            // 资源未修改，返回 304 Not Modified
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
        }

        // 资源已修改，返回资源内容，并附带 Last-Modified 响应头
        Instant now = Instant.now();
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.LAST_MODIFIED, DateTimeFormatter.RFC_1123_DATE_TIME
                .withZone(ZoneOffset.UTC).format(now));

        return new ResponseEntity<>("pap.net.cn! " + now.toString(), headers, HttpStatus.OK);
    }

    @GetMapping("/eTagTest")
    public ResponseEntity<String> eTagTest() {

        if (new Random().nextBoolean()) {
            // 资源未修改，返回 304 Not Modified
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
        }

        Instant now = Instant.now();
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ETAG, DateTimeFormatter.RFC_1123_DATE_TIME
                .withZone(ZoneOffset.UTC).format(now));

        return new ResponseEntity<>("pap.net.cn! " + now.toString(), headers, HttpStatus.OK);
    }

    /**
     * 实现字段 proguardIdx 针对每个 proguardName 分组自增，并保证高并发安全。
     * 核心思路： 辅助表存储最新序号 建一个表 proguard_id_seq，每行记录一个 proguardName 的当前最大 seq 值。 事务 + 行级锁获取最新 seq
     * 在创建新记录时，通过事务获取对应 user_id 的行。 使用 PESSIMISTIC_WRITE 锁住该行，确保同一时间只有一个线程可以更新 last_seq。
     * 计算下一个 seq 并更新辅助表 下一个 seq = last_seq + 1 更新 user_seq 表的 last_seq 为新值
     * @param proguardName
     * @return
     */
    @GetMapping("/saveProguardWithIdxSeq")
    public ResponseEntity<Proguard> saveProguardWithIdxSeq(@RequestParam(required = false, defaultValue = "proguardName") String proguardName) {
        Proguard proguard = new Proguard();
        proguard.setProguardId(System.currentTimeMillis());
        proguard.setProguardName(proguardName);

        Map<String, Object> extMap = new HashMap<>();
        extMap.put("timeswap", System.currentTimeMillis());
        extMap.put("threadId", Thread.currentThread().getName());
        proguard.setExtMap(extMap);

        List<String> extList = new ArrayList<>();
        extList.add("A");
        extList.add("B");
        extList.add("C");
        extList.add("D");
        proguard.setExtList(extList);

        Map<String, Object> abstractMap = new HashMap<>();
        abstractMap.put("extMap", extMap);
        abstractMap.put("extList", extList);
        abstractMap.put("long", 1l);
        abstractMap.put("float", 1.23f);
        abstractMap.put("boolean", true);

        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arrayNode = mapper.createArrayNode();
        JsonNode nestedObject = mapper.valueToTree(abstractMap);
        arrayNode.add(nestedObject);

        ObjectNode objectNode = mapper.valueToTree(abstractMap);
        proguard.setAbstractObj(objectNode);
        proguard.setAbstractList(arrayNode);

        return new ResponseEntity<>(proguardService.saveProguardWithIdxSeq(proguard), HttpStatus.OK);
    }

    @GetMapping("/saveAndFlush")
    public ResponseEntity<Proguard> saveAndFlush() {
        Proguard proguard = new Proguard();
        proguard.setProguardId(System.currentTimeMillis());
        proguard.setProguardName(proguard.getProguardId() + "");

        Map<String, Object> extMap = new HashMap<>();
        extMap.put("timeswap", System.currentTimeMillis());
        extMap.put("threadId", Thread.currentThread().getName());
        proguard.setExtMap(extMap);

        List<String> extList = new ArrayList<>();
        extList.add("A");
        extList.add("B");
        extList.add("C");
        extList.add("D");
        proguard.setExtList(extList);

        Map<String, Object> abstractMap = new HashMap<>();
        abstractMap.put("extMap", extMap);
        abstractMap.put("extList", extList);
        abstractMap.put("long", 1l);
        abstractMap.put("float", 1.23f);
        abstractMap.put("boolean", true);

        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arrayNode = mapper.createArrayNode();
        JsonNode nestedObject = mapper.valueToTree(abstractMap);
        arrayNode.add(nestedObject);

        ObjectNode objectNode = mapper.valueToTree(abstractMap);
        proguard.setAbstractObj(objectNode);
        proguard.setAbstractList(arrayNode);

        return new ResponseEntity<>(proguardService.saveAndFlush(proguard), HttpStatus.OK);
    }

    @GetMapping("/getProguardByProguardId")
    public Proguard getProguardByProguardId(@RequestParam(name = "proguardId") Long proguardId) {
        return proguardService.getProguardByProguardId(proguardId);
    }

    @GetMapping("updateProguardByProguardId")
    public Proguard updateProguardByProguardId(@RequestParam(name = "proguardId") Long proguardId) {
        Proguard proguardByProguardId = proguardService.getProguardByProguardId(proguardId);
        proguardByProguardId.setProguardName("update : " + System.currentTimeMillis());
        return proguardService.updateProguardByProguardId(proguardByProguardId);
    }

    @GetMapping("/searchAllByProguardName")
    public List<Proguard> searchAllByProguardName(@RequestParam(name = "proguardName") String proguardName) {
        return proguardService.searchAllByProguardName(proguardName);
    }

    @GetMapping("/findAll")
    public List<Proguard> findAll() {
        return proguardService.findAll();
    }

    @GetMapping("/findNaive")
    public Page<Proguard> findNaive() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Proguard> proguardsPageable = proguardService.searchAllByNaiveSQL("select * from proguard order by proguard_id desc", pageable);
        return proguardsPageable;
    }

    @GetMapping("/saveAllAndFlush")
    public List<Proguard> saveAllAndFlush() {
        List<Proguard> proguards = new ArrayList<>();
        for(int idx = 0; idx < 100; idx++) {
            Proguard proguard = new Proguard();
            proguard.setProguardId(System.currentTimeMillis());
            proguard.setProguardName(proguard.getProguardId() + "");
            proguards.add(proguard);
        }
        return proguardService.saveAllAndFlush(proguards);
    }

    @GetMapping("/saveAllAndFlush2")
    public Boolean saveAllAndFlush2() {
        Proguard proguard = new Proguard();
        proguard.setProguardId(1l);
        proguard.setProguardName(proguard.getProguardId() + "");

        Map<String, Object> extMap = new HashMap<>();
        extMap.put("timeswap", System.currentTimeMillis());
        extMap.put("threadId", Thread.currentThread().getName());
        proguard.setExtMap(extMap);

        List<String> extList = new ArrayList<>();
        extList.add("A");
        extList.add("B");
        extList.add("C");
        extList.add("D");
        proguard.setExtList(extList);

        Map<String, Object> abstractMap = new HashMap<>();
        abstractMap.put("extMap", extMap);
        abstractMap.put("extList", extList);
        abstractMap.put("long", 1l);
        abstractMap.put("float", 1.23f);
        abstractMap.put("boolean", true);

        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arrayNode = mapper.createArrayNode();
        JsonNode nestedObject = mapper.valueToTree(abstractMap);
        arrayNode.add(nestedObject);
        proguard.setAbstractList(arrayNode);

        ObjectNode objectNode = mapper.valueToTree(abstractMap);
        proguard.setAbstractObj(objectNode);

        return proguardService.saveAndFlush2(proguard, new Proguard());
    }


    @GetMapping("/saveAndUpdate")
    public Proguard saveAndUpdate() {
        Proguard proguard = new Proguard();
        proguard.setProguardId(System.currentTimeMillis());
        proguard.setProguardName(proguard.getProguardId() + "");

        Map<String, Object> extMap = new HashMap<>();
        extMap.put("timeswap", System.currentTimeMillis());
        extMap.put("threadId", Thread.currentThread().getName());
        proguard.setExtMap(extMap);

        List<String> extList = new ArrayList<>();
        extList.add("A");
        extList.add("B");
        extList.add("C");
        extList.add("D");
        proguard.setExtList(extList);

        Map<String, Object> abstractMap = new HashMap<>();
        abstractMap.put("extMap", extMap);
        abstractMap.put("extList", extList);
        abstractMap.put("long", 1l);
        abstractMap.put("float", 1.23f);
        abstractMap.put("boolean", true);

        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arrayNode = mapper.createArrayNode();
        JsonNode nestedObject = mapper.valueToTree(abstractMap);
        arrayNode.add(nestedObject);
        proguard.setAbstractList(arrayNode);

        ObjectNode objectNode = mapper.valueToTree(abstractMap);
        proguard.setAbstractObj(objectNode);

        proguardService.saveAndFlush(proguard);

        proguard.setProguardName("update");
        proguardService.saveAndFlush(proguard);

        return proguard;
    }

    @PostMapping("postSave")
    @ResponseBody
    public Proguard postSave(@RequestBody Proguard proguard) {
        proguard.setProguardId(System.currentTimeMillis());
        proguardService.saveAndFlush(proguard);
        return proguard;
    }

    @GetMapping(value = "/exceptionRandom", produces = "application/json;charset=UTF-8")
    public String exceptionRandom() {
        Boolean b = proguardService.exceptionRandom("alexgaoyh");
        return b.toString();
    }

    @GetMapping(value = "/longtime", produces = "application/json;charset=UTF-8")
    public String longtime() {
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return "longtime";
    }

    @GetMapping("/timeout")
    public Proguard timeout(@RequestParam(name = "timeoutMS") Long timeoutMS) {
        Proguard proguard = new Proguard();
        proguard.setProguardId(1l);
        proguard.setProguardName(proguard.getProguardId() + "");

        Map<String, Object> extMap = new HashMap<>();
        extMap.put("timeswap", System.currentTimeMillis());
        extMap.put("threadId", Thread.currentThread().getName());
        proguard.setExtMap(extMap);

        List<String> extList = new ArrayList<>();
        extList.add("A");
        extList.add("B");
        extList.add("C");
        extList.add("D");
        proguard.setExtList(extList);

        Map<String, Object> abstractMap = new HashMap<>();
        abstractMap.put("extMap", extMap);
        abstractMap.put("extList", extList);
        abstractMap.put("long", 1l);
        abstractMap.put("float", 1.23f);
        abstractMap.put("boolean", true);

        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arrayNode = mapper.createArrayNode();
        JsonNode nestedObject = mapper.valueToTree(abstractMap);
        arrayNode.add(nestedObject);
        proguard.setAbstractList(arrayNode);

        ObjectNode objectNode = mapper.valueToTree(abstractMap);
        proguard.setAbstractObj(objectNode);

        proguardService.timeout(proguard, timeoutMS);
        return proguard;
    }

    @GetMapping("dataSourcePrintProguardId")
    public String dataSourcePrintProguardId(HttpServletRequest request, HttpServletResponse response) {
        proguardService.dataSourcePrintProguardId();
        return "success";
    }

    /**
     * 前端传递不同对象的 json ，然后持久化 DB
     * 注意这里需要手动注册实体类，便于找到对应的对象
     * @param entityName
     * @param json
     * @return
     */
    @PostMapping("/saveOrUpdateSignalCRUD/{entityName}")
    public Object saveOrUpdateSignalCRUD(@PathVariable String entityName, @RequestBody String json) throws JsonProcessingException {
        Class<?> entityClass = entityMappings.get(entityName);
        Object object = objectMapper.readValue(json, entityClass);
        return transactionTemplate.execute(status -> {
            try {
                Object savedEntity = entityManager.merge(object);
                return savedEntity;
            } catch (Exception e) {
                status.setRollbackOnly();
                throw new RuntimeException("保存失败: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 避免深度分页，增加延迟关联分页， 相关验证代码
     * @param proguardName
     * @param pageNumber
     * @param pageSize
     * @return
     * @throws Exception
     */
    @GetMapping("pageByProguardNameDeepPaging")
    public Page<Proguard> pageByProguardNameDeepPaging(@RequestParam(name = "proguardName", required = false) String proguardName,
                                                       @RequestParam(name = "pageNumber", required = false, defaultValue = "1") Integer pageNumber,
                                                       @RequestParam(name = "pageSize", required = false, defaultValue = "10") Integer pageSize) throws Exception {
        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        Page<Proguard> proguards = proguardService.pageByProguardNameDeepPaging(proguardName, pageable);
        return proguards;
    }

    /**
     * 增加一个字符串，里面直接存 json, 然后界面进行展示的时候还是原始 json
     * @param request
     * @param response
     * @return
     * @throws Exception
     */
    @GetMapping("saveJson")
    public Proguard saveJson(HttpServletRequest request, HttpServletResponse response) throws Exception {
        Proguard proguard = initProguard();
        ObjectMapper mapper = new ObjectMapper();
        String jsonStr = mapper.writeValueAsString(proguard);
        proguard.setJsonSchema(jsonStr);
        // proguard.setJsonData(mapper.valueToTree(proguard.getAbstractList()));
        Proguard proguard1 = proguardService.saveAndFlush(proguard);
        return proguard1;
    }

    public Proguard initProguard() {
        Proguard proguard = new Proguard();
        proguard.setProguardId(System.currentTimeMillis());
        proguard.setProguardName(proguard.getProguardId() + "");

        Map<String, Object> extMap = new HashMap<>();
        extMap.put("timeswap", System.currentTimeMillis());
        extMap.put("threadId", Thread.currentThread().getName());
        proguard.setExtMap(extMap);

        List<String> extList = new ArrayList<>();
        extList.add("A");
        extList.add("B");
        extList.add("C");
        extList.add("D");
        proguard.setExtList(extList);

        Map<String, Object> abstractMap = new HashMap<>();
        abstractMap.put("extMap", extMap);
        abstractMap.put("extList", extList);
        abstractMap.put("long", 1l);
        abstractMap.put("float", 1.23f);
        abstractMap.put("boolean", true);

        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arrayNode = mapper.createArrayNode();
        JsonNode nestedObject = mapper.valueToTree(abstractMap);
        arrayNode.add(nestedObject);

        ObjectNode objectNode = mapper.valueToTree(abstractMap);
        proguard.setAbstractObj(objectNode);
        proguard.setAbstractList(arrayNode);

        return proguard;
    }

}
