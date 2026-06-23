package cn.net.pap.example.proguard.controller;

import cn.net.pap.example.proguard.json.JsonRawWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/json")
@Tag(name = "JSON处理测试接口", description = "演示特殊 JSON 格式包装输出（如原始字符串转 JSON 节点）的测试接口")
public class JsonController {

    @Operation(summary = "获取未包裹的 Map")
    @GetMapping("/map1")
    public Map<String, Object> map1() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", "Tom");
        map.put("extraJson", "{\"age\":18}");
        return map;
    }

    @Operation(summary = "获取包裹后的 Map（特殊序列化）")
    @GetMapping("/map2")
    public MappingJacksonValue map2() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", "Tom");
        map.put("extraJson", "{\"age\":18}");
        return JsonRawWrapper.wrap(map, Set.of("extraJson"));
    }

    @Operation(summary = "获取未包裹的 Map 列表")
    @GetMapping("/list1")
    public List<Map<String, Object>> list1() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", "Tom");
        map.put("extraJson", "{\"age\":18}");
        return List.of(map);
    }

    @Operation(summary = "获取包裹后的 Map 列表")
    @GetMapping("/list2")
    public MappingJacksonValue list2() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", "Tom");
        map.put("extraJson", "{\"age\":18}");
        return JsonRawWrapper.wrap(List.of(map), Set.of("extraJson"));
    }

    @Operation(summary = "获取未包裹的 DTO")
    @GetMapping("/dto1")
    public JsonDTO dto1() {
        JsonDTO jsonDTO = new JsonDTO();
        jsonDTO.setName("Tom");
        jsonDTO.setExtraJson("{\"age\":18}");
        return jsonDTO;
    }

    @Operation(summary = "获取包裹后的 DTO")
    @GetMapping("/dto2")
    public MappingJacksonValue dto2() {
        JsonDTO jsonDTO = new JsonDTO();
        jsonDTO.setName("Tom");
        jsonDTO.setExtraJson("{\"age\":18}");
        return JsonRawWrapper.wrap(jsonDTO, Set.of("extraJson"));
    }

    @Operation(summary = "获取未包裹的 DTO 列表")
    @GetMapping("/list3")
    public List list3() {
        JsonDTO jsonDTO = new JsonDTO();
        jsonDTO.setName("Tom");
        jsonDTO.setExtraJson("{\"age\":18}");
        return List.of(jsonDTO);
    }

    @Operation(summary = "获取包裹后的 DTO 列表")
    @GetMapping("/list4")
    public MappingJacksonValue list4() {
        JsonDTO jsonDTO = new JsonDTO();
        jsonDTO.setName("Tom");
        jsonDTO.setExtraJson("{\"age\":18}");
        return JsonRawWrapper.wrap(List.of(jsonDTO), Set.of("extraJson"));
    }

    @Operation(summary = "获取包裹后并包裹在 Map 的 ResponseEntity")
    @GetMapping("/responseEntity1")
    public ResponseEntity<MappingJacksonValue> responseEntity1() {
        JsonDTO jsonDTO = new JsonDTO();
        jsonDTO.setName("Tom");
        jsonDTO.setExtraJson("{\"age\":18}");

        Map<String, Object> map = new HashMap<>();
        map.put("code", 200);
        map.put("data", jsonDTO);
        return ResponseEntity.ok(JsonRawWrapper.wrap(map, Set.of("extraJson")));
    }

    @Operation(summary = "获取包裹在特定结果类的 ResponseEntity")
    @GetMapping("/responseEntity2")
    public ResponseEntity<MappingJacksonValue> responseEntity2() {
        JsonDTO jsonDTO = new JsonDTO();
        jsonDTO.setName("Tom");
        jsonDTO.setExtraJson("{\"age\":18}");

        JsonResult jsonResult = new JsonResult();
        jsonResult.setCode(200);
        jsonResult.setData(jsonDTO);
        return ResponseEntity.ok(JsonRawWrapper.wrap(jsonResult, Set.of("extraJson")));
    }

    class JsonResult {
        private Integer code;
        private JsonDTO data;

        public Integer getCode() {
            return code;
        }

        public void setCode(Integer code) {
            this.code = code;
        }

        public JsonDTO getData() {
            return data;
        }

        public void setData(JsonDTO data) {
            this.data = data;
        }
    }

    class JsonDTO {
        private String name;
        private String extraJson;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getExtraJson() {
            return extraJson;
        }

        public void setExtraJson(String extraJson) {
            this.extraJson = extraJson;
        }
    }

}
