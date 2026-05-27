package dev.scx.data.sql.sql_builder;

import dev.scx.data.sql.schema_mapping.EntityTable;
import dev.scx.sql.dialect.Dialect;

/// BaseSQLBuilder
///
/// @author scx567888
/// @version 0.0.1
public class BaseSQLBuilder {

    public final EntityTable<?> table;
    public final Dialect dialect;

    public BaseSQLBuilder(EntityTable<?> table, Dialect dialect) {
        this.table = table;
        this.dialect = dialect;
    }

    public String getTableName() {
        return dialect.quoteIdentifier(table.name());
    }

    public String getWhereClause(String whereClause) {
        return whereClause != null && !whereClause.isEmpty() ? " WHERE " + whereClause : "";
    }

    public String getOrderByClause(String[] orderByClauses) {
        return orderByClauses != null && orderByClauses.length != 0 ? " ORDER BY " + String.join(", ", orderByClauses) : "";
    }

}
