package cn.net.pap.example.doris.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;

@Data
@TableName(value = "doris", autoResultMap = true)
public class Doris {

    private Long id;

    private String dorisName;

    private String dorisRemark;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private ObjectNode dorisJson;

}
