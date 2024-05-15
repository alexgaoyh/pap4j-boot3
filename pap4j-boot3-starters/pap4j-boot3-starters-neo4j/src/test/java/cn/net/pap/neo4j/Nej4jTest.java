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

    // @Test
    public void insert() {
        String currentTimeMillis = System.currentTimeMillis() + "";

        HobbyEntity hobbyEntity1 = new HobbyEntity();
        hobbyEntity1.setHobbyId(currentTimeMillis);
        hobbyEntity1.setHobbyName(currentTimeMillis);

        HobbyEntity hobbyEntity2 = new HobbyEntity();
        hobbyEntity2.setHobbyId(currentTimeMillis + "_1");
        hobbyEntity2.setHobbyName(currentTimeMillis + "_1");

        PersonEntity personEntity1 = new PersonEntity();
        personEntity1.setPersonId(currentTimeMillis);
        personEntity1.setPersonName(currentTimeMillis);
        personEntity1.setDescription(currentTimeMillis);
        List<HobbyEntity> hobbyEntityList12 = new ArrayList<>();
        hobbyEntityList12.add(hobbyEntity1);
        hobbyEntityList12.add(hobbyEntity2);
        personEntity1.setHobbys(hobbyEntityList12);

        PersonEntity personEntity2 = new PersonEntity();
        personEntity2.setPersonId(currentTimeMillis + "_1");
        personEntity2.setPersonName(currentTimeMillis + "_1");
        personEntity2.setDescription(currentTimeMillis + "_1");
        List<PersonEntity> personEntities1 = new ArrayList<>();
        personEntities1.add(personEntity1);
        personEntity2.setParents(personEntities1);

        hobbyRepository.save(hobbyEntity1);
        hobbyRepository.save(hobbyEntity2);
        personRepository.save(personEntity1);
        personRepository.save(personEntity2);

    }

}
