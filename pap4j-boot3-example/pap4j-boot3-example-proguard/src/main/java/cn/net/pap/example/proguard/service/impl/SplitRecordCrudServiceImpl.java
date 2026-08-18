package cn.net.pap.example.proguard.service.impl;

import cn.net.pap.example.proguard.dto.SplitRecordDTO;
import cn.net.pap.example.proguard.exception.SplitRecordException;
import cn.net.pap.example.proguard.service.ISplitRecordCrudService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 分表记录读写实现：基于 {@link JdbcTemplate} 的 native SQL 动态表名读写。
 * 表名白名单内联于本类（DB 安全红线，防 SQL 注入）；v1 只实现建表 / 保存 / 单条查询。
 */
@Service
public class SplitRecordCrudServiceImpl implements ISplitRecordCrudService {

    /** SELECT 列清单（与业务表 / {@link SplitRecordDTO} 对应） */
    private static final String COLUMNS = "id, data, ext_str_1, ext_str_2, ext_num_1";

    /** 写全 3 个 ext 列（缺省 null） */
    private static final String EXT_COLUMNS = "ext_str_1, ext_str_2, ext_num_1";

    /** 表名白名单：字母/下划线开头，最长 64 字符（进 SQL 字符串前必须命中，防注入） */
    private static final Pattern TABLE_NAME = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]{0,63}$");

    private final JdbcTemplate jdbcTemplate;

    public SplitRecordCrudServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void createTableIfNotExists(String tableName) {
        String safeTable = requireTable(tableName);
        String ddl = """
                CREATE TABLE IF NOT EXISTS %s (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    data TEXT NOT NULL,
                    ext_str_1 VARCHAR(255), ext_str_2 VARCHAR(255),
                    ext_num_1 DECIMAL(20,6)
                )""".formatted(safeTable);
        jdbcTemplate.execute(ddl);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(String tableName, SplitRecordDTO dto) {
        Objects.requireNonNull(dto, "dto 不能为 null");
        String safeTable = requireTable(tableName);
        if (dto.getData() == null) {
            throw new SplitRecordException("data 不能为 null");
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO %s (data, %s)
                    VALUES (?, ?, ?, ?)""".formatted(safeTable, EXT_COLUMNS), Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, dto.getData());
            ps.setString(2, dto.getExtStr1());
            ps.setString(3, dto.getExtStr2());
            ps.setBigDecimal(4, dto.getExtNum1());
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new SplitRecordException("取回自增 id 失败，表: " + safeTable);
        }
        dto.setId(key.longValue());
        return key.longValue();
    }

    @Override
    public SplitRecordDTO get(String tableName, Long id) {
        String safeTable = requireTable(tableName);
        requireId(id);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT %s FROM %s WHERE id = ?""".formatted(COLUMNS, safeTable), id);
        if (rows.isEmpty()) {
            return null;
        }
        return mapRow(rows.get(0));
    }

    /**
     * 按列名把查询结果映射进 {@link SplitRecordDTO}。
     */
    private SplitRecordDTO mapRow(Map<String, Object> row) {
        SplitRecordDTO dto = new SplitRecordDTO();
        dto.setId(((Number) row.get("id")).longValue());
        dto.setData((String) row.get("data"));
        dto.setExtStr1((String) row.get("ext_str_1"));
        dto.setExtStr2((String) row.get("ext_str_2"));
        dto.setExtNum1((BigDecimal) row.get("ext_num_1"));
        return dto;
    }

    // ---------------------------------------------------------------- 白名单（DB 安全红线）

    private static String requireTable(String tableName) {
        if (tableName == null || !TABLE_NAME.matcher(tableName).matches()) {
            throw new SplitRecordException("非法表名: " + tableName);
        }
        return tableName;
    }

    private static void requireId(Long id) {
        if (id == null) {
            throw new SplitRecordException("id 不能为 null");
        }
    }
}
