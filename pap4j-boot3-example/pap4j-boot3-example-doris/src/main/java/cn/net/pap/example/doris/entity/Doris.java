package cn.net.pap.example.doris.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("doris")
public class Doris {

    private Long id;

    private String dorisName;

    private String dorisRemark;

    private Object dorisJson;

}
