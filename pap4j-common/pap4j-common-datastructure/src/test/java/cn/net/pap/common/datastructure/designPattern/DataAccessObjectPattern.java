package cn.net.pap.common.datastructure.designPattern;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class DataAccessObjectPattern {

    class Student {
        private String name;
        private int rollNo;

        Student(String name, int rollNo) {
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

    interface StudentDao {
        public List<Student> getAllStudents();

        public Student getStudent(int rollNo);

        public void updateStudent(Student student);

        public void deleteStudent(Student student);
    }

    class StudentDaoImpl implements StudentDao {

        //列表是当作一个数据库
        List<Student> students;

        public StudentDaoImpl() {
            students = new ArrayList<Student>();
            Student student1 = new Student("Robert", 0);
            Student student2 = new Student("John", 1);
            students.add(student1);
            students.add(student2);
        }

        @Override
        public void deleteStudent(Student student) {
            students.remove(student.getRollNo());
            System.out.println("Student: Roll No " + student.getRollNo()
                    + ", deleted from database");
        }

        //从数据库中检索学生名单
        @Override
        public List<Student> getAllStudents() {
            return students;
        }

        @Override
        public Student getStudent(int rollNo) {
            return students.get(rollNo);
        }

        @Override
        public void updateStudent(Student student) {
            students.get(student.getRollNo()).setName(student.getName());
            System.out.println("Student: Roll No " + student.getRollNo()
                    + ", updated in the database");
        }
    }

    @Test
    public void test() {
        StudentDao studentDao = new StudentDaoImpl();

        //输出所有的学生
        for (Student student : studentDao.getAllStudents()) {
            System.out.println("Student: [RollNo : "
                    + student.getRollNo() + ", Name : " + student.getName() + " ]");
        }


        //更新学生
        Student student = studentDao.getAllStudents().get(0);
        student.setName("Michael");
        studentDao.updateStudent(student);

        //获取学生
        studentDao.getStudent(0);
        System.out.println("Student: [RollNo : "
                + student.getRollNo() + ", Name : " + student.getName() + " ]");
    }
}
