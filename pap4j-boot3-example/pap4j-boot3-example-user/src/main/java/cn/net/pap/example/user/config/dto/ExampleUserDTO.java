package cn.net.pap.example.user.config.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class ExampleUserDTO implements Serializable {

    private String userName;

    private String sex;
}
