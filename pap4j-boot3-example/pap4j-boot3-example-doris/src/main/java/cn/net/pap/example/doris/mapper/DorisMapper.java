package cn.net.pap.example.doris.mapper;

import cn.net.pap.example.doris.entity.Doris;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;
import java.util.Map;

public interface DorisMapper extends BaseMapper<Doris> {

    public List<Map<String, Object>> getDataBySql(String value);

    public int updateBySql(String value);

    public int deleteBySql(String value);

}
