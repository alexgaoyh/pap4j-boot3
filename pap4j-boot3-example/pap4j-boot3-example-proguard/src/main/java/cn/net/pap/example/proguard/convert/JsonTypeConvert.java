package cn.net.pap.example.proguard.convert;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Objects;

/**
 * 使用方式如下，会存储成 json 类型的字段，请注意当前类在 nullSafeGet 函数里面，在解析之前对数据库中存储的字符串做了额外处理，移除前后双引号和斜杠.
 *     @Type(value = JsonTypeConvert.class)
 *     @Column(nullable = false, columnDefinition = "json")
 *     private Map<String, Object> extMap = new HashMap<>();
 */
public class JsonTypeConvert implements UserType<Object> {

    @Override
    public int getSqlType() {
        return Types.OTHER;
    }

    @Override
    public Class<Object> returnedClass() {
        return Object.class;
    }

    @Override
    public boolean equals(Object x, Object y) {
        return Objects.equals(x, y);
    }

    @Override
    public int hashCode(Object x) {
        return x.hashCode();
    }

    @Override
    public Object nullSafeGet(ResultSet resultSet, int i, SharedSessionContractImplementor sharedSessionContractImplementor, Object o) throws SQLException {
        String cellContent = resultSet.getString(i);
        if (cellContent == null) {
            return null;
        }
        try {
            // 注意这里存的数据被双引号包裹，并且包括 /， 都需要把他移除掉.
            return new ObjectMapper().readValue(cellContent.substring(1, cellContent.length() - 1).replaceAll("\\\\", ""), Object.class);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to convert String to Object: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void nullSafeSet(PreparedStatement st, Object value, int index, SharedSessionContractImplementor session) throws SQLException {
        if (value == null) {
            st.setNull(index, Types.OTHER);
            return;
        }
        try {
            st.setObject(index, new ObjectMapper().writeValueAsString(value), Types.OTHER);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to convert Object to String: " + ex.getMessage(), ex);
        }
    }

    @Override
    public Object deepCopy(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new ObjectMapper().readValue(new ObjectMapper().writeValueAsString(value), Object.class);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to deep copy Object: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean isMutable() {
        return true;
    }

    @Override
    public Serializable disassemble(Object value) {
        return (Serializable) value;
    }

    @Override
    public Object assemble(Serializable cached, Object owner) {
        return cached;
    }

    @Override
    public Object replace(Object original, Object target, Object owner) {
        return original;
    }
}

