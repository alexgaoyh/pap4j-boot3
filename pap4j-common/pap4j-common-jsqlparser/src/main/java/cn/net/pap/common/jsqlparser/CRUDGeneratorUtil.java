package cn.net.pap.common.jsqlparser;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * CURD SQL generator util
 */
public class CRUDGeneratorUtil {

    /**
     * select
     * @param clazz
     * @return
     */
    public static String generateSelectSQL(Class<?> clazz) {
        StringBuilder sql = new StringBuilder("SELECT ");
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            if (field.isAnnotationPresent(JsonProperty.class)) {
                JsonProperty jsonProperty = field.getAnnotation(JsonProperty.class);
                sql.append(toUnderScoreCase(jsonProperty.value())).append(", ");
            }
        }
        sql.delete(sql.length() - 2, sql.length());  // 去掉最后的逗号
        sql.append(" FROM ").append(clazz.getSimpleName().toLowerCase());
        return sql.toString();
    }

    /**
     * insert
     * @param entity
     * @return
     * @throws IllegalAccessException
     */
    public static String generateInsertSQL(Object entity) throws IllegalAccessException {
        StringBuilder sql = new StringBuilder("INSERT INTO ");
        Class<?> clazz = entity.getClass();
        sql.append(clazz.getSimpleName().toLowerCase()).append(" (");

        Field[] fields = clazz.getDeclaredFields();
        List<String> values = new ArrayList<>();
        for (Field field : fields) {
            if (field.isAnnotationPresent(JsonProperty.class)) {
                field.setAccessible(true);
                JsonProperty jsonProperty = field.getAnnotation(JsonProperty.class);
                sql.append(toUnderScoreCase(jsonProperty.value())).append(", ");
                values.add("'" + field.get(entity) + "'");
            }
        }
        sql.delete(sql.length() - 2, sql.length());  // 去掉最后的逗号
        sql.append(") VALUES (").append(String.join(", ", values)).append(")");
        return sql.toString();
    }

    /**
     * update
     * @param entity
     * @return
     * @throws IllegalAccessException
     */
    public static String generateUpdateSQL(Object entity) throws IllegalAccessException {
        StringBuilder sql = new StringBuilder("UPDATE ");
        Class<?> clazz = entity.getClass();
        sql.append(clazz.getSimpleName().toLowerCase()).append(" SET ");

        Field[] fields = clazz.getDeclaredFields();
        List<String> setClauses = new ArrayList<>();
        String whereClause = "";
        for (Field field : fields) {
            field.setAccessible(true);
            if (field.isAnnotationPresent(JsonProperty.class)) {
                JsonProperty jsonProperty = field.getAnnotation(JsonProperty.class);
                if (field.getName().equals("id")) {  // 假设 id 是主键
                    whereClause = toUnderScoreCase(jsonProperty.value()) + " = " + field.get(entity);
                } else {
                    setClauses.add(toUnderScoreCase(jsonProperty.value()) + " = '" + field.get(entity) + "'");
                }
            }
        }

        sql.append(String.join(", ", setClauses));
        sql.append(" WHERE ").append(whereClause);
        return sql.toString();
    }

    /**
     * delete
     * @param entity
     * @return
     * @throws IllegalAccessException
     */
    public static String generateDeleteSQL(Object entity) throws IllegalAccessException {
        StringBuilder sql = new StringBuilder("DELETE FROM ");
        Class<?> clazz = entity.getClass();
        sql.append(clazz.getSimpleName().toLowerCase()).append(" WHERE ");

        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
            if (field.isAnnotationPresent(JsonProperty.class) && field.getName().equals("id")) {
                JsonProperty jsonProperty = field.getAnnotation(JsonProperty.class);
                sql.append(toUnderScoreCase(jsonProperty.value())).append(" = ").append(field.get(entity));
            }
        }
        return sql.toString();
    }

    /**
     * 驼峰 to 下划线
     * @param camelCaseStr
     * @return
     */
    public static String toUnderScoreCase(String camelCaseStr) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < camelCaseStr.length(); i++) {
            char c = camelCaseStr.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    builder.append('_');
                }
                builder.append(Character.toLowerCase(c));
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }
}
