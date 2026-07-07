package dev.scx.data.sql.sql_builder;

import dev.scx.data.LockMode;
import dev.scx.data.field_policy.FieldPolicy;
import dev.scx.data.query.Query;
import dev.scx.data.sql.parser.SQLOrderByParser;
import dev.scx.data.sql.parser.SQLWhereParser;
import dev.scx.data.sql.schema_mapping.EntityTable;
import dev.scx.sql.SQL;
import dev.scx.sql.dialect.Dialect;

import static dev.scx.array.ScxArray.concat;
import static dev.scx.data.LockMode.EXCLUSIVE;
import static dev.scx.data.LockMode.SHARED;
import static dev.scx.data.sql.sql_builder.SQLBuilderHelper.filterByQueryFieldPolicy;
import static dev.scx.data.sql.sql_builder.SQLBuilderHelper.joinWithQuoteIdentifier;
import static dev.scx.sql.SQL.sql;

/// SelectSQLBuilder
///
/// @author scx567888
public final class SelectSQLBuilder extends BaseSQLBuilder {

    private final SQLWhereParser whereParser;
    private final SQLOrderByParser orderByParser;

    public SelectSQLBuilder(EntityTable<?> table, Dialect dialect, SQLWhereParser whereParser, SQLOrderByParser orderByParser) {
        super(table, dialect);
        this.whereParser = whereParser;
        this.orderByParser = orderByParser;
    }

    /// 创建虚拟查询列
    public static String[] createVirtualSelectColumns(FieldPolicy fieldPolicy, Dialect dialect) {
        var fieldExpressions = fieldPolicy.getVirtualFields();
        var virtualSelectColumns = new String[fieldExpressions.length];
        int i = 0;
        for (var fieldExpression : fieldExpressions) {
            var fieldName = fieldExpression.virtualFieldName();
            var expression = fieldExpression.expression();
            // 这个虚拟列 因为可能在表中不存在 所以此处不进行名称映射了 直接引用包装一下即可
            virtualSelectColumns[i] = expression + " AS " + dialect.quoteIdentifier(fieldName);
            i = i + 1;
        }
        return virtualSelectColumns;
    }

    /// lockMode 可以为 null
    public SQL buildSelectSQL(Query query, FieldPolicy fieldPolicy, LockMode lockMode) {
        // 1, 过滤查询列
        var selectColumns = filterByQueryFieldPolicy(fieldPolicy, table);
        // 2, 创建虚拟查询列
        var virtualSelectColumns = createVirtualSelectColumns(fieldPolicy, dialect);
        // 3, 创建最终查询列
        var finalSelectColumns = concat(selectColumns, virtualSelectColumns);
        // 4, 创建 where 子句
        var whereClause = whereParser.parse(query.getWhere());
        // 5, 创建 orderBy 子句
        var orderByClauses = orderByParser.parse(query.getOrderBys());
        // 6, 创建 SQL
        var sqlStr = getSelectSQL(finalSelectColumns, whereClause.expression(), orderByClauses, query.getOffset(), query.getLimit(), lockMode);
        return sql(sqlStr, whereClause.params());
    }

    /// lockMode 可以为 null
    public SQL buildSelectFirstSQL(Query query, FieldPolicy fieldPolicy, LockMode lockMode) {
        // 1, 过滤查询列
        var selectColumns = filterByQueryFieldPolicy(fieldPolicy, table);
        // 2, 创建虚拟查询列
        var virtualSelectColumns = createVirtualSelectColumns(fieldPolicy, dialect);
        // 3, 创建最终查询列
        var finalSelectColumns = concat(selectColumns, virtualSelectColumns);
        // 4, 创建 where 子句
        var whereClause = whereParser.parse(query.getWhere());
        // 5, 创建 orderBy 子句
        var orderByClauses = orderByParser.parse(query.getOrderBys());
        // 6, 创建 SQL
        var sqlStr = getSelectSQL(finalSelectColumns, whereClause.expression(), orderByClauses, null, 1L, lockMode);
        return sql(sqlStr, whereClause.params());
    }

    public SQL buildSelectSQL(Query query, FieldPolicy fieldPolicy) {
        return buildSelectSQL(query, fieldPolicy, null);
    }

    public SQL buildSelectFirstSQL(Query query, FieldPolicy fieldPolicy) {
        return buildSelectFirstSQL(query, fieldPolicy, null);
    }

    private String getSelectSQL(Object[] selectColumns, String whereClause, String[] orderByClauses, Long offset, Long limit, LockMode lockMode) {
        if (selectColumns.length == 0) {
            throw new IllegalArgumentException("Select 子句错误 : 待查询的数据列 不能为空 !!!");
        }
        var sqlStr = "SELECT " + getSelectColumns(selectColumns) + " FROM " + getTableName() + getWhereClause(whereClause) + getOrderByClause(orderByClauses);
        sqlStr = dialect.applyLimit(sqlStr, offset, limit);
        if (lockMode == SHARED) {
            sqlStr = dialect.applySharedLock(sqlStr);
        } else if (lockMode == EXCLUSIVE) {
            sqlStr = dialect.applyExclusiveLock(sqlStr);
        }
        return sqlStr;
    }

    private String getSelectColumns(Object[] selectColumns) {
        return joinWithQuoteIdentifier(selectColumns, dialect);
    }

}
