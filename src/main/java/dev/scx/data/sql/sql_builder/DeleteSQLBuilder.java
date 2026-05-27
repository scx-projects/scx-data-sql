package dev.scx.data.sql.sql_builder;

import dev.scx.data.query.Query;
import dev.scx.data.sql.parser.SQLOrderByParser;
import dev.scx.data.sql.parser.SQLWhereParser;
import dev.scx.data.sql.schema_mapping.EntityTable;
import dev.scx.sql.SQL;
import dev.scx.sql.dialect.Dialect;

import static dev.scx.sql.SQL.sql;

/// DeleteSQLBuilder
///
/// @author scx567888
/// @version 0.0.1
public final class DeleteSQLBuilder extends BaseSQLBuilder {

    private final SQLWhereParser whereParser;
    private final SQLOrderByParser orderByParser;

    public DeleteSQLBuilder(EntityTable<?> table, Dialect dialect, SQLWhereParser whereParser, SQLOrderByParser orderByParser) {
        super(table, dialect);
        this.whereParser = whereParser;
        this.orderByParser = orderByParser;
    }

    public SQL buildDeleteSQL(Query query) {
        var whereClause = whereParser.parse(query.getWhere());
        var orderByClauses = orderByParser.parse(query.getOrderBys());
        var sqlStr = GetDeleteSQL(whereClause.expression(), orderByClauses, query.getLimit());
        return sql(sqlStr, whereClause.params());
    }

    public String GetDeleteSQL(String whereClause, String[] orderByClauses, Long limit) {
        if (whereClause == null || whereClause.isEmpty()) {
            throw new IllegalArgumentException("删除数据时 必须指定 删除条件 或 自定义的 where 语句 !!!");
        }
        var sqlStr = "DELETE FROM " + getTableName() + getWhereClause(whereClause) + getOrderByClause(orderByClauses);
        // 删除时 limit 不能有 offset (偏移量)
        return dialect.applyLimit(sqlStr, null, limit);
    }

}
