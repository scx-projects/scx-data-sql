package dev.scx.data.sql.sql_builder;

import dev.scx.data.query.Query;
import dev.scx.data.sql.parser.SQLWhereParser;
import dev.scx.data.sql.schema_mapping.EntityTable;
import dev.scx.sql.SQL;
import dev.scx.sql.dialect.Dialect;

import static dev.scx.sql.SQL.sql;

/// CountSQLBuilder
///
/// @author scx567888
/// @version 0.0.1
public final class CountSQLBuilder extends BaseSQLBuilder {

    private final SQLWhereParser whereParser;

    public CountSQLBuilder(EntityTable<?> table, Dialect dialect, SQLWhereParser whereParser) {
        super(table, dialect);
        this.whereParser = whereParser;
    }

    public SQL buildCountSQL(Query query) {
        var whereClause = whereParser.parse(query.getWhere());
        var sqlStr = GetCountSQL(whereClause.expression());
        return sql(sqlStr, whereClause.params());
    }

    private String GetCountSQL(String whereClause) {
        return "SELECT COUNT(*) AS count FROM " + getTableName() + getWhereClause(whereClause);
    }

}
