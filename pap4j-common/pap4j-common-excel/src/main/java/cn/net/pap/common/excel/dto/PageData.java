package cn.net.pap.common.excel.dto;

import java.io.Serializable;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.*;
import java.util.stream.Collectors;

public class PageData extends LinkedHashMap implements Map, Serializable {

    private static final long serialVersionUID = 1L;

    private Map map = null;

    private String request;

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
            e.printStackTrace();
        }
        map = returnMap;
    }

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
