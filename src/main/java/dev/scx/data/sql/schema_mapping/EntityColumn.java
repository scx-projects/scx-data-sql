package dev.scx.data.sql.schema_mapping;

import dev.scx.reflect.FieldInfo;
import dev.scx.sql.schema.Column;

/// EntityColumn
///
/// @author scx567888
/// @version 0.0.1
public interface EntityColumn extends Column {

    FieldInfo javaField();

}
