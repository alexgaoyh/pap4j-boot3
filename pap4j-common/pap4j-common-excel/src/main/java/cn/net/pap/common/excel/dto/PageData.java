package cn.net.pap.common.excel.dto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class PageData extends LinkedHashMap implements Map, Serializable {

    private static final Logger log = LoggerFactory.getLogger(PageData.class);

    private static final long serialVersionUID = 1L;

    private Map map = null;

    private String request;

    /**
     * 通过请求参数字符串构造 PageData。
     *
     * @param request 请求参数字符串（key=value&key2=value2 格式）
     */
    public PageData(String request) {
        this.request = request;
        Map properties = stringToMap(request);
        Map returnMap = new LinkedHashMap();
        Iterator entries = properties.entrySet().iterator();
        Entry entry;
        String name = "";
        String value = "";
        while (entries.hasNext()) {
            entry = (Entry) entries.next();
            name = (String) entry.getKey();
            Object valueObj = entry.getValue();
            if (null == valueObj) {
                value = "";
            } else if (valueObj instanceof String[]) {
                String[] values = (String[]) valueObj;
                for (int i = 0; i < values.length; i++) {
                    value = values[i] + ",";
                }
                value = value.substring(0, value.length() - 1);
            } else {
                value = valueObj.toString();
            }
            returnMap.put(name, value);
        }

        map = returnMap;
    }

    public PageData() {
        map = new LinkedHashMap();
    }

    /**
     * 通过查询结果集构造 PageData。
     *
     * @param res 数据库查询结果集
     */
    public PageData(ResultSet res) {
        Map returnMap = new LinkedHashMap();
        try {
            ResultSetMetaData rsmd = res.getMetaData();
            int count = rsmd.getColumnCount();
            for (int i = 1; i <= count; i++) {
                String key = rsmd.getColumnLabel(i);
                String value = res.getString(i);
                returnMap.put(key, value);
            }
        } catch (Exception e) {
            log.error("PageData", e);
        }
        map = returnMap;
    }

    /**
     * 将请求参数字符串解析为 Map。
     *
     * @param request 请求参数字符串
     * @return 解析后的键值对
     */
    public Map stringToMap(String request) {
        String res[] = request.split("&");
        Map resMap = new LinkedHashMap<>();
        for (int i = 0; i < res.length; i++) {
            String obj[] = res[i].split("=");
            resMap.put(obj[0], obj[1]);
        }
        return resMap;

    }

    public String getString(Object key) {
        return (String) map.get(key);
    }

    /**
     * 按索引获取值的字符串表示。
     *
     * @param idx 条目索引
     * @return 对应索引的字符串值
     */
    public String getStringByIdx(Integer idx) {
        List<Map.Entry<String, Object>> list = (List<Entry<String, Object>>) map.entrySet().stream().collect(Collectors.toList());
        Map.Entry<String, Object> entry = list.get(idx);
        return (String) entry.getValue();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object put(Object key, Object value) {
        return map.put(key, value);
    }

    @Override
    public Object remove(Object key) {
        return map.remove(key);
    }

    public void clear() {
        map.clear();
    }

    public boolean containsKey(Object key) {
        return map.containsKey(key);
    }

    public boolean containsValue(Object value) {
        return map.containsValue(value);
    }

    public Set entrySet() {
        return map.entrySet();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public Set keySet() {
        return map.keySet();
    }

    @SuppressWarnings("unchecked")
    public void putAll(Map t) {
        map.putAll(t);
    }

    public int size() {
        return map.size();
    }

    public Collection values() {
        return map.values();
    }

}
