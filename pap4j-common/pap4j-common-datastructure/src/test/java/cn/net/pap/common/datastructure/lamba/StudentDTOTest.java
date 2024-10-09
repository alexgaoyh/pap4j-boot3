package cn.net.pap.common.datastructure.lamba;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StudentDTOTest {

    @Test
    public void lambaTest() {
        StudentDTO student = new StudentDTO.Builder()
                .setFirstName("alex")
                .setLastName("gaoyh")
                .setAge(35)
                .setEmail("https://pap-docs.pap.net.cn/")
                .build();

        assertEquals("alex", student.getFirstName());
        assertEquals("gaoyh", student.getLastName());
        assertEquals(35, student.getAge());
        assertEquals("https://pap-docs.pap.net.cn/", student.getEmail());

    }

}
