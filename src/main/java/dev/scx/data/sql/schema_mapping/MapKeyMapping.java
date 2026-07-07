package dev.scx.data.sql.schema_mapping;

import java.util.function.Function;

/// 这个映射表示 列名 -> 字段
///
/// @author scx567888
public final class MapKeyMapping implements Function<String, String> {

    private final EntityTable<?> table;

    public MapKeyMapping(EntityTable<?> table) {
        this.table = table;
    }

    @Override
    public String apply(String columnName) {
        var column = this.table.getColumn(columnName);
        return column == null ? null : column.javaField().name();
    }

}
