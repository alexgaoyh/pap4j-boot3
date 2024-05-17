package cn.net.pap.neo4j;

import cn.net.pap.neo4j.entity.HobbyEntity;
import cn.net.pap.neo4j.entity.PersonEntity;
import cn.net.pap.neo4j.repository.HobbyRepository;
import cn.net.pap.neo4j.repository.PersonRepository;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {Neo4jApplication.class})
public class Nej4jTest {

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private HobbyRepository hobbyRepository;

    //@Test
    public void insert() {
        String currentTimeMillis = System.currentTimeMillis() + "";

        HobbyEntity hobbyEntity1 = new HobbyEntity();
        hobbyEntity1.setHobbyId(currentTimeMillis);
        hobbyEntity1.setHobbyName("H1");

        HobbyEntity hobbyEntity2 = new HobbyEntity();
        hobbyEntity2.setHobbyId(currentTimeMillis + "_1");
        hobbyEntity2.setHobbyName("H2");

        PersonEntity personEntity1 = new PersonEntity();
        personEntity1.setPersonId(currentTimeMillis);
        personEntity1.setPersonName("P1");
        personEntity1.setDescription("D1");
        List<HobbyEntity> hobbyEntityList12 = new ArrayList<>();
        hobbyEntityList12.add(hobbyEntity1);
        hobbyEntityList12.add(hobbyEntity2);
        personEntity1.setHobbys(hobbyEntityList12);

        PersonEntity personEntity2 = new PersonEntity();
        personEntity2.setPersonId(currentTimeMillis + "_1");
        personEntity2.setPersonName("P2");
        personEntity2.setDescription("D2");
        List<PersonEntity> personEntities1 = new ArrayList<>();
        personEntities1.add(personEntity1);
        personEntity2.setParents(personEntities1);

        hobbyRepository.save(hobbyEntity1);
        hobbyRepository.save(hobbyEntity2);
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
}
