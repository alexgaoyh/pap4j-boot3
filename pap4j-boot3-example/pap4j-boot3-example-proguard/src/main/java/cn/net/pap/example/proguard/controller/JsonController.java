package cn.net.pap.example.proguard.controller;

import cn.net.pap.example.proguard.json.JsonRawWrapper;
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
public class JsonController {

    @GetMapping("/map1")
    public Map<String, Object> map1() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", "Tom");
        map.put("extraJson", "{\"age\":18}");
        return map;
    }

    @GetMapping("/map2")
    public MappingJacksonValue map2() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", "Tom");
        map.put("extraJson", "{\"age\":18}");
        return JsonRawWrapper.wrap(map, Set.of("extraJson"));
    }

    @GetMapping("/list1")
    public List<Map<String, Object>> list1() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", "Tom");
        map.put("extraJson", "{\"age\":18}");
        return List.of(map);
    }

    @GetMapping("/list2")
    public MappingJacksonValue list2() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", "Tom");
        map.put("extraJson", "{\"age\":18}");
        return JsonRawWrapper.wrap(List.of(map), Set.of("extraJson"));
    }

    @GetMapping("/dto1")
    public JsonDTO dto1() {
        JsonDTO jsonDTO = new JsonDTO();
        jsonDTO.setName("Tom");
        jsonDTO.setExtraJson("{\"age\":18}");
        return jsonDTO;
    }

    @GetMapping("/dto2")
    public MappingJacksonValue dto2() {
        JsonDTO jsonDTO = new JsonDTO();
        jsonDTO.setName("Tom");
        jsonDTO.setExtraJson("{\"age\":18}");
        return JsonRawWrapper.wrap(jsonDTO, Set.of("extraJson"));
    }

    @GetMapping("/list3")
    public List list3() {
        JsonDTO jsonDTO = new JsonDTO();
        jsonDTO.setName("Tom");
        jsonDTO.setExtraJson("{\"age\":18}");
        return List.of(jsonDTO);
    }

    @GetMapping("/list4")
    public MappingJacksonValue list4() {
        JsonDTO jsonDTO = new JsonDTO();
        jsonDTO.setName("Tom");
        jsonDTO.setExtraJson("{\"age\":18}");
        return JsonRawWrapper.wrap(List.of(jsonDTO), Set.of("extraJson"));
    }

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
