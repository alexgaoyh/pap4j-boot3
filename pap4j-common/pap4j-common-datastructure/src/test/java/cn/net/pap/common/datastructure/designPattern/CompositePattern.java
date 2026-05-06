package cn.net.pap.common.datastructure.designPattern;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;


public class CompositePattern {

    private static final Logger log = LoggerFactory.getLogger(CompositePattern.class);

    class Employee {
        private String name;
        private String dept;
        private int salary;
        private List<Employee> subordinates;

        //构造函数
        public Employee(String name, String dept, int sal) {
            this.name = name;
            this.dept = dept;
            this.salary = sal;
            subordinates = new ArrayList<Employee>();
        }

        public void add(Employee e) {
            subordinates.add(e);
        }

        public void remove(Employee e) {
            subordinates.remove(e);
        }

        public List<Employee> getSubordinates() {
            return subordinates;
        }

        @Override
        public String toString() {
            return ("Employee :[ Name : " + name
                    + ", dept : " + dept + ", salary :"
                    + salary + " ]");
        }
    }

    @Test
    public void test() {
        Employee CEO = new Employee("John", "CEO", 30000);

        Employee headSales = new Employee("Robert", "Head Sales", 20000);

        Employee headMarketing = new Employee("Michel", "Head Marketing", 20000);

        Employee clerk1 = new Employee("Laura", "Marketing", 10000);
        Employee clerk2 = new Employee("Bob", "Marketing", 10000);

        Employee salesExecutive1 = new Employee("Richard", "Sales", 10000);
        Employee salesExecutive2 = new Employee("Rob", "Sales", 10000);

        CEO.add(headSales);
        CEO.add(headMarketing);

        headSales.add(salesExecutive1);
        headSales.add(salesExecutive2);

        headMarketing.add(clerk1);
        headMarketing.add(clerk2);

        //打印该组织的所有员工
        log.info("{}", CEO);
        for (Employee headEmployee : CEO.getSubordinates()) {
            log.info("{}", headEmployee);
            for (Employee employee : headEmployee.getSubordinates()) {
                log.info("{}", employee);
            }
        }
    }

}
