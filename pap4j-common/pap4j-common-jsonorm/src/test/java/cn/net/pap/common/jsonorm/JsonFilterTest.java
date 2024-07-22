package cn.net.pap.common.jsonorm;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class JsonFilterTest {

    @Test
    public void jsonFilterTest() throws Exception {
        TypeThird third1 = new TypeThird();
        third1.setId("t1");
        third1.setName("t1");
        TypeThird third2 = new TypeThird();
        third2.setId("t2");
        third2.setName("t2");
        TypeThird third3 = new TypeThird();
        third3.setId("t3");
        third3.setName("t3");
        TypeThird third4 = new TypeThird();
        third4.setId("t4");
        third4.setName("t4");

        TypeSecond second1 = new TypeSecond();
        second1.setId("s1");
        second1.setName("s1");
        List<TypeThird> typeThirds1 = new ArrayList<>();
        typeThirds1.add(third1);
        typeThirds1.add(third2);
        second1.setDetails(typeThirds1);

        TypeSecond second2 = new TypeSecond();
        second2.setId("s2");
        second2.setName("s2");
        List<TypeThird> typeThirds2 = new ArrayList<>();
        typeThirds2.add(third3);
        typeThirds2.add(third4);
        second2.setDetails(typeThirds2);

        TypeOne one = new TypeOne();
        one.setId("o1");
        one.setName("o1");
        List<TypeSecond> seconds = new ArrayList<>();
        seconds.add(second1);
        seconds.add(second2);
        one.setDetails(seconds);

        ObjectMapper objectMapper = new ObjectMapper();
        SimpleBeanPropertyFilter filter = SimpleBeanPropertyFilter.serializeAllExcept("name");
        FilterProvider filters = new SimpleFilterProvider().addFilter("pap4j-boot3-type-filter", filter);
        String after = objectMapper.setFilterProvider(filters).writeValueAsString(one);
        System.out.println(after);
    }


    @JsonFilter("pap4j-boot3-type-filter")
    class TypeOne implements Serializable {

        private String id;

        private String name;

        private List<TypeSecond> details;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<TypeSecond> getDetails() {
            return details;
        }

        public void setDetails(List<TypeSecond> details) {
            this.details = details;
        }
    }

    @JsonFilter("pap4j-boot3-type-filter")
    class TypeSecond implements Serializable {

        private String id;

        private String name;

        private List<TypeThird> details;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<TypeThird> getDetails() {
            return details;
        }

        public void setDetails(List<TypeThird> details) {
            this.details = details;
        }
    }

    @JsonFilter("pap4j-boot3-type-filter")
    class TypeThird implements Serializable {

        private String id;

        private String name;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}


