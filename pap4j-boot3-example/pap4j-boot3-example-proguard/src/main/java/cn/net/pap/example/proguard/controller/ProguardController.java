package cn.net.pap.example.proguard.controller;

import cn.net.pap.example.proguard.entity.Proguard;
import cn.net.pap.example.proguard.service.IProguardService;
import cn.net.pap.example.proguard.util.SimpleRateLimiter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
public class ProguardController {

    @Autowired
    private IProguardService proguardService;

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

    @GetMapping("getProguardByProguardId")
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

}
