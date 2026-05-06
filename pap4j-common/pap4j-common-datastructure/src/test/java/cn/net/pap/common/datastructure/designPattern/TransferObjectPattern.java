package cn.net.pap.common.datastructure.designPattern;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class TransferObjectPattern {

    private static final Logger log = LoggerFactory.getLogger(TransferObjectPattern.class);

    class StudentVO {
        private String name;
        private int rollNo;

        StudentVO(String name, int rollNo) {
            this.name = name;
            this.rollNo = rollNo;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getRollNo() {
            return rollNo;
        }

        public void setRollNo(int rollNo) {
            this.rollNo = rollNo;
        }
    }

    class StudentBO {

        //列表是当作一个数据库
        List<StudentVO> students;

        public StudentBO() {
            students = new ArrayList<StudentVO>();
            StudentVO student1 = new StudentVO("Robert", 0);
            StudentVO student2 = new StudentVO("John", 1);
            students.add(student1);
            students.add(student2);
        }

        public void deleteStudent(StudentVO student) {
            students.remove(student.getRollNo());
            log.info("Student: Roll No {}, deleted from database", student.getRollNo());
        }

        //从数据库中检索学生名单
        public List<StudentVO> getAllStudents() {
            return students;
        }

        public StudentVO getStudent(int rollNo) {
            return students.get(rollNo);
        }

        public void updateStudent(StudentVO student) {
            students.get(student.getRollNo()).setName(student.getName());
            log.info("Student: Roll No {}, updated in the database", student.getRollNo());
        }
    }

    @Test
    public void test() {
        StudentBO studentBusinessObject = new StudentBO();

        //输出所有的学生
        for (StudentVO student : studentBusinessObject.getAllStudents()) {
            log.info("Student: [RollNo : {}, Name : {} ]", student.getRollNo(), student.getName());
        }

        //更新学生
        StudentVO student = studentBusinessObject.getAllStudents().get(0);
        student.setName("Michael");
        studentBusinessObject.updateStudent(student);

        //获取学生
        studentBusinessObject.getStudent(0);
        log.info("Student: [RollNo : {}, Name : {} ]", student.getRollNo(), student.getName());
    }

}
