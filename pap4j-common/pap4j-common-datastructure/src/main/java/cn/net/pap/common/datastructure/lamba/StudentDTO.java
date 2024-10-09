package cn.net.pap.common.datastructure.lamba;

import java.io.Serializable;

/**
 * simple lamba design
 */
public class StudentDTO implements Serializable {

    private final String firstName;
    private final String lastName;
    private final int age;
    private final String email;

    // 私有构造函数
    private StudentDTO(Builder builder) {
        this.firstName = builder.firstName;
        this.lastName = builder.lastName;
        this.age = builder.age;
        this.email = builder.email;
    }


    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public int getAge() {
        return age;
    }

    public String getEmail() {
        return email;
    }

    // Builder 内部类
    public static class Builder {
        private String firstName;
        private String lastName;
        private int age;
        private String email;

        public Builder setFirstName(String firstName) {
            this.firstName = firstName;
            return this;
        }

        public Builder setLastName(String lastName) {
            this.lastName = lastName;
            return this;
        }

        public Builder setAge(int age) {
            this.age = age;
            return this;
        }

        public Builder setEmail(String email) {
            this.email = email;
            return this;
        }

        public StudentDTO build() {
            return new StudentDTO(this);
        }
    }

}