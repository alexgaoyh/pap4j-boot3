package cn.net.pap.common.datastructure.serializable;

import java.io.Serializable;

public class NonParamConstructor implements Serializable {

    private String name;
    private int age;

    // 只有有参构造方法
    public NonParamConstructor(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    @Override
    public String toString() {
        return "Person{name='" + name + "', age=" + age + "}";
    }
}
