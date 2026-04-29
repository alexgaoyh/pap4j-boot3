package cn.net.pap.neo4j;

import cn.net.pap.neo4j.entity.HobbyEntity;
import cn.net.pap.neo4j.entity.PersonEntity;
import cn.net.pap.neo4j.repository.HobbyRepository;
import cn.net.pap.neo4j.repository.PersonRepository;
import cn.net.pap.neo4j.serializer.jackson.PersonEntitySerializer;
import cn.net.pap.neo4j.util.kg.PersonEntity2KGConvert;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = {Neo4jApplication.class})
@org.springframework.test.context.TestConstructor(autowireMode = org.springframework.test.context.TestConstructor.AutowireMode.ALL)
public class Nej4jTest extends Neo4jBaseTest {

    private final PersonRepository personRepository;
    private final HobbyRepository hobbyRepository;

    public Nej4jTest(PersonRepository personRepository, HobbyRepository hobbyRepository) {
        this.personRepository = personRepository;
        this.hobbyRepository = hobbyRepository;
    }

    //@Test
    public void insert() {

        HobbyEntity hobbyEntity1 = new HobbyEntity();
        hobbyEntity1.setHobbyId("H1");
        hobbyEntity1.setHobbyName("H1");

        HobbyEntity hobbyEntity2 = new HobbyEntity();
        hobbyEntity2.setHobbyId("H2");
        hobbyEntity2.setHobbyName("H2");

        HobbyEntity hobbyEntity3 = new HobbyEntity();
        hobbyEntity3.setHobbyId("H3");
        hobbyEntity3.setHobbyName("H3");

        PersonEntity personEntity1 = new PersonEntity();
        personEntity1.setPersonId("P1");
        personEntity1.setPersonName("P1");
        personEntity1.setDescription("D1");
        List<HobbyEntity> hobbyEntityList12 = new ArrayList<>();
        hobbyEntityList12.add(hobbyEntity1);
        hobbyEntityList12.add(hobbyEntity2);
        personEntity1.setHobbys(hobbyEntityList12);

        PersonEntity personEntity2 = new PersonEntity();
        personEntity2.setPersonId("P2");
        personEntity2.setPersonName("P2");
        personEntity2.setDescription("D2");
        List<HobbyEntity> hobbyEntityList3 = new ArrayList<>();
        hobbyEntityList3.add(hobbyEntity3);
        personEntity2.setHobbys(hobbyEntityList3);

        List<PersonEntity> personEntities2 = new ArrayList<>();
        personEntities2.add(personEntity2);
        personEntity1.setChildrens(personEntities2);

        List<PersonEntity> personEntities1 = new ArrayList<>();
        personEntities1.add(personEntity1);
        personEntity2.setParents(personEntities1);

        hobbyRepository.save(hobbyEntity1);
        hobbyRepository.save(hobbyEntity2);
        hobbyRepository.save(hobbyEntity3);
        personRepository.save(personEntity1);
        personRepository.save(personEntity2);
    }

    //@Test
    public void update() {
        List<PersonEntity> all = personRepository.findAll();
        if(all != null && all.size() > 0) {
            for(PersonEntity personEntity : all) {
                personEntity.setPersonName(personEntity.getPersonName() + "_T");
                personRepository.save(personEntity);
            }
        }
    }

    //@Test
    public void delete() {
        personRepository.deleteAll();
    }

    //@Test
    public void getPerson() {
        List<PersonEntity> p2 = personRepository.findByPersonName("P2");
        // 验证这里能不能查出来一对多的关联信息.
        assertTrue(p2.get(0).getHobbys().size() > 0);

        Map<String, Object> kgGraph = PersonEntity2KGConvert.convertToKnowledgeGraph(p2.get(0));
        assertTrue(!kgGraph.isEmpty());

        try {
            // 在 PersonEntity.java 类上添加注解 @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "personId")
            ObjectMapper objectMapperJsonIdentityInfo = new ObjectMapper();
            String jsonIdentityInfoStr = objectMapperJsonIdentityInfo.writeValueAsString(p2);
            System.out.println(jsonIdentityInfoStr);
        } catch (JsonProcessingException e) {
        }

        try {
            ObjectMapper objectMapperSerializer = new ObjectMapper();
            SimpleModule module = new SimpleModule();
            module.addSerializer(PersonEntity.class, new PersonEntitySerializer());
            objectMapperSerializer.registerModule(module);
            String serializerStr = objectMapperSerializer.writeValueAsString(p2);
            System.out.println(serializerStr);
        } catch (JsonProcessingException e) {
        }

    }
}
