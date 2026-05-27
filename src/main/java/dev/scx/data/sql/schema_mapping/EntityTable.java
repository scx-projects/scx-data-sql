package dev.scx.data.sql.schema_mapping;

import dev.scx.sql.schema.Table;

/// EntityTable
///
/// @author scx567888
/// @version 0.0.1
public interface EntityTable<Entity> extends Table {

    Class<Entity> entityClass();

    @Override
    EntityColumn[] columns();

    @Override
    EntityColumn getColumn(String name);

}
