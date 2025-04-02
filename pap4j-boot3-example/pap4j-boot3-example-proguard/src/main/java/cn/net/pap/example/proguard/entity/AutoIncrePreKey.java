package cn.net.pap.example.proguard.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Comment;

@Entity
@Table(name = "autoIncrePreKey")
public class AutoIncrePreKey {

    @Id
    // 此处配合 AutoIncrePreKeyTest.preKeyTest 来验证主键是否能够保证连续的新增.
    // 同时可以对照 GenerationType 不同参数之间的区别， 主键连续与否: GenerationType.SEQUENCE GenerationType.IDENTITY
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Comment(value = "主键")
    private Long id;

    @Column(length = 50, nullable = false)
    @Comment(value = "名称")
    private String name;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "AutoIncrePreKey{" +
                "id=" + id +
                ", name='" + name + '\'' +
                '}';
    }

}
