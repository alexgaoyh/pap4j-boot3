package cn.net.pap.common.jsqlparser;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class JsonToSqlConverter {

    private static final Logger log = LoggerFactory.getLogger(JsonToSqlConverter.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public record QueryJson(
            @JsonProperty("main_table") String mainTable,
            @JsonProperty("columns") List<String> columns,
            @JsonProperty("joins") List<JoinInfo> joins,
            @JsonProperty("filters") List<FilterInfo> filters,
            @JsonProperty("groups") List<String> groups,
            @JsonProperty("aggs") List<AggInfo> aggs,
            @JsonProperty("orders") List<OrderInfo> orders,
            @JsonProperty("limit") Integer limit,
            @JsonProperty("offset") Integer offset
    ) {
    }

    public record JoinInfo(
            @JsonProperty("join_table") String joinTable,
            @JsonProperty("on_left") String onLeft,
            @JsonProperty("on_right") String onRight,
            @JsonProperty("type") String type
    ) {
    }

    public record FilterInfo(
            @JsonProperty("logic") String logic,
            @JsonProperty("conditions") List<FilterInfo> conditions,
            @JsonProperty("field") String field,
            @JsonProperty("operator") String operator,
            @JsonProperty("value") Object value
    ) {
    }

    public record AggInfo(
            @JsonProperty("function") String function,
            @JsonProperty("field") String field,
            @JsonProperty("alias") String alias
    ) {
    }

    public record OrderInfo(
            @JsonProperty("field") String field,
            @JsonProperty("direction") String direction
    ) {
    }

    public static String convert(String json) throws IOException {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("JSON string cannot be null or empty");
        }
        QueryJson query = OBJECT_MAPPER.readValue(json, QueryJson.class);
        if (query == null) {
            throw new IllegalArgumentException("Invalid JSON structure: parsed query object is null");
        }
        String sql = buildSql(query);
        validateSql(sql);
        return sql;
    }

    public static void validateSql(String sql) {
        try {
            CCJSqlParserUtil.parse(sql);
        } catch (Exception e) {
            log.error("Generated SQL is invalid: {}", sql, e);
            throw new IllegalArgumentException("Invalid generated SQL: " + e.getMessage(), e);
        }
    }

    private static String buildSql(QueryJson query) {
        StringBuilder sql = new StringBuilder();
        sql.append(buildSelect(query));
        sql.append(buildFrom(query));
        sql.append(buildJoins(query));
        sql.append(buildWhere(query));
        sql.append(buildGroupBy(query));
        sql.append(buildOrderBy(query));
        sql.append(buildLimitOffset(query));
        return sql.toString().trim();
    }

    private static String buildSelect(QueryJson query) {
        List<String> selectItems = new ArrayList<>();
        if (query.groups() != null) {
            for (String group : query.groups()) {
                if (group != null && !group.isBlank()) {
                    selectItems.add(group);
                }
            }
        }
        if (query.aggs() != null) {
            for (AggInfo agg : query.aggs()) {
                if (agg == null) {
                    continue;
                }
                if (agg.function() == null || agg.function().isBlank()) {
                    throw new IllegalArgumentException("Aggregation function is required");
                }
                if (agg.field() == null || agg.field().isBlank()) {
                    throw new IllegalArgumentException("Aggregation field is required");
                }
                String expr = agg.function() + "(" + agg.field() + ")";
                if (agg.alias() != null && !agg.alias().isBlank()) {
                    expr += " AS " + agg.alias();
                }
                selectItems.add(expr);
            }
        }
        if (selectItems.isEmpty() && query.columns() != null) {
            for (String col : query.columns()) {
                if (col != null && !col.isBlank()) {
                    selectItems.add(col);
                }
            }
        }
        if (selectItems.isEmpty()) {
            selectItems.add("*");
        }
        return "SELECT " + String.join(", ", selectItems);
    }

    private static String buildFrom(QueryJson query) {
        if (query.mainTable() == null || query.mainTable().isBlank()) {
            throw new IllegalArgumentException("main_table is required");
        }
        return " FROM " + query.mainTable();
    }

    private static String buildJoins(QueryJson query) {
        if (query.joins() == null || query.joins().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JoinInfo join : query.joins()) {
            if (join == null) {
                continue;
            }
            if (join.joinTable() == null || join.joinTable().isBlank()) {
                throw new IllegalArgumentException("join_table is required for join");
            }
            if (join.onLeft() == null || join.onLeft().isBlank()) {
                throw new IllegalArgumentException("on_left is required for join");
            }
            if (join.onRight() == null || join.onRight().isBlank()) {
                throw new IllegalArgumentException("on_right is required for join");
            }
            String type = join.type() != null && !join.type().isBlank() ? join.type().toUpperCase() : "LEFT";
            sb.append(" ").append(type).append(" JOIN ").append(join.joinTable())
                    .append(" ON ").append(join.onLeft()).append(" = ").append(join.onRight());
        }
        return sb.toString();
    }

    private static String buildWhere(QueryJson query) {
        if (query.filters() == null || query.filters().isEmpty()) {
            return "";
        }
        List<String> filterSqls = new ArrayList<>();
        for (FilterInfo filter : query.filters()) {
            if (filter == null) {
                continue;
            }
            String sql = buildFilterSql(filter);
            if (sql != null && !sql.isBlank()) {
                filterSqls.add(sql);
            }
        }
        if (filterSqls.isEmpty()) {
            return "";
        }
        return " WHERE " + String.join(" AND ", filterSqls);
    }

    private static String buildFilterSql(FilterInfo filter) {
        if (filter == null) {
            return "";
        }
        if (filter.logic() != null && !filter.logic().isBlank()) {
            String logicOp = filter.logic().toUpperCase();
            if (!"AND".equals(logicOp) && !"OR".equals(logicOp)) {
                throw new IllegalArgumentException("logic operator must be AND or OR");
            }
            if (filter.conditions() == null || filter.conditions().isEmpty()) {
                return "";
            }
            List<String> list = new ArrayList<>();
            for (FilterInfo cond : filter.conditions()) {
                String sub = buildFilterSql(cond);
                if (sub != null && !sub.isBlank()) {
                    list.add(sub);
                }
            }
            if (list.isEmpty()) {
                return "";
            }
            if (list.size() == 1) {
                return list.get(0);
            }
            return "(" + String.join(" " + logicOp + " ", list) + ")";
        } else if (filter.field() != null && !filter.field().isBlank()) {
            return buildConditionSql(filter);
        }
        return "";
    }

    private static String buildConditionSql(FilterInfo filter) {
        String field = filter.field();
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("field is required for condition");
        }
        String op = filter.operator() != null && !filter.operator().isBlank() ? filter.operator().toUpperCase() : "EQUALS";
        Object val = filter.value();

        switch (op) {
            case "IS_NULL" -> {
                return field + " IS NULL";
            }
            case "IS_NOT_NULL" -> {
                return field + " IS NOT NULL";
            }
            case "EQUALS" -> {
                return field + " = " + formatValue(val);
            }
            case "NOT_EQUALS" -> {
                return field + " != " + formatValue(val);
            }
            case "GREATER_THAN" -> {
                return field + " > " + formatValue(val);
            }
            case "LESS_THAN" -> {
                return field + " < " + formatValue(val);
            }
            case "GREATER_OR_EQUALS" -> {
                return field + " >= " + formatValue(val);
            }
            case "LESS_OR_EQUALS" -> {
                return field + " <= " + formatValue(val);
            }
            case "LIKE" -> {
                return field + " LIKE " + formatValue(val);
            }
            case "IN" -> {
                return field + " IN " + formatCollectionValue(val);
            }
            case "NOT_IN" -> {
                return field + " NOT IN " + formatCollectionValue(val);
            }
            default -> throw new IllegalArgumentException("Unsupported operator: " + op);
        }
    }

    private static String formatValue(Object val) {
        if (val == null) {
            return "NULL";
        }
        if (val instanceof String str) {
            return "'" + str.replace("'", "''") + "'";
        }
        return val.toString();
    }

    private static String formatCollectionValue(Object val) {
        if (val == null) {
            return "(NULL)";
        }
        if (val instanceof Collection<?> col) {
            return "(" + col.stream().map(JsonToSqlConverter::formatValue).collect(Collectors.joining(", ")) + ")";
        }
        return "(" + formatValue(val) + ")";
    }

    private static String buildGroupBy(QueryJson query) {
        if (query.groups() == null || query.groups().isEmpty()) {
            return "";
        }
        List<String> validGroups = new ArrayList<>();
        for (String group : query.groups()) {
            if (group != null && !group.isBlank()) {
                validGroups.add(group);
            }
        }
        if (validGroups.isEmpty()) {
            return "";
        }
        return " GROUP BY " + String.join(", ", validGroups);
    }

    private static String buildOrderBy(QueryJson query) {
        if (query.orders() == null || query.orders().isEmpty()) {
            return "";
        }
        List<String> list = new ArrayList<>();
        for (OrderInfo order : query.orders()) {
            if (order == null) {
                continue;
            }
            if (order.field() == null || order.field().isBlank()) {
                throw new IllegalArgumentException("field is required for order");
            }
            String dir = order.direction() != null && !order.direction().isBlank() ? order.direction().toUpperCase() : "ASC";
            list.add(order.field() + " " + dir);
        }
        return " ORDER BY " + String.join(", ", list);
    }

    private static String buildLimitOffset(QueryJson query) {
        StringBuilder sb = new StringBuilder();
        if (query.limit() != null) {
            sb.append(" LIMIT ").append(query.limit());
        }
        if (query.offset() != null) {
            sb.append(" OFFSET ").append(query.offset());
        }
        return sb.toString();
    }
}
