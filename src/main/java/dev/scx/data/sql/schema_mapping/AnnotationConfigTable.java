package dev.scx.data.sql.schema_mapping;

import dev.scx.data.sql.annotation.NoColumn;
import dev.scx.data.sql.annotation.Table;
import dev.scx.reflect.ClassInfo;
import dev.scx.reflect.FieldInfo;
import dev.scx.reflect.ScxReflect;
import dev.scx.sql.schema.Index;
import dev.scx.sql.schema.Key;
import dev.scx.sql.schema.definition.IndexDefinition;
import dev.scx.sql.schema.definition.KeyDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static dev.scx.reflect.AccessModifier.PUBLIC;
import static dev.scx.reflect.ClassKind.RECORD;

/// AnnotationConfigTable
///
/// @author scx567888
/// @version 0.0.1
public final class AnnotationConfigTable<Entity> implements EntityTable<Entity> {

    /// 实体类 class
    private final Class<Entity> entityClass;

    /// 表名
    private final String name;

    /// 实体类型不含 @NoColumn 注解的 field 对应的列
    private final AnnotationConfigColumn[] columns;

    /// Key
    private final Key[] keys;

    /// Index
    private final Index[] indexes;

    /// 因为 循环查找速度太慢了 所以这里 使用 map加速 (key:javaField.name,value:ColumnInfo)
    private final Map<String, AnnotationConfigColumn> columnMap;

    public AnnotationConfigTable(Class<Entity> entityClass) {
        this.entityClass = entityClass;
        this.name = initTableName(entityClass);
        this.columns = initAllColumns(entityClass);
        this.keys = initKeys(this.name, this.columns);
        this.indexes = initIndexes(this.columns);
        // 性能优化
        this.columnMap = initAllColumnMap(this.columns);
    }

    /// 获取表名, 必须标注 Table 注解
    private static String initTableName(Class<?> clazz) {
        var table = clazz.getAnnotation(Table.class);
        if (table == null) {
            throw new IllegalArgumentException("entityClass 必须标注 @Table 注解");
        }
        return table.value();
    }

    private static AnnotationConfigColumn[] initAllColumns(Class<?> clazz) {
        var typeInfo = ScxReflect.typeOf(clazz);
        if (!(typeInfo instanceof ClassInfo classInfo)) {
            throw new IllegalArgumentException("entityClass 必须是 bean 类型, type: " + clazz);
        }
        FieldInfo[] fields;
        if (classInfo.classKind() == RECORD) {
            // record 我们取所有 field
            fields = classInfo.allFields();
        } else {
            // 普通类 我们取所有 public field
            fields = Stream.of(classInfo.allFields())
                .filter(c -> c.accessModifier() == PUBLIC)
                .toArray(FieldInfo[]::new);
        }
        var list = Stream.of(fields)
            .filter(field -> field.findAnnotation(NoColumn.class) == null)
            .map(AnnotationConfigColumn::new)
            .toList();
        checkDuplicateColumnName(list, clazz);
        return list.toArray(AnnotationConfigColumn[]::new);
    }

    /// 检测 columnName 重复值
    ///
    /// @param list  a
    /// @param clazz a
    private static void checkDuplicateColumnName(List<AnnotationConfigColumn> list, Class<?> clazz) {
        var columnMap = list.stream().collect(Collectors.groupingBy(AnnotationConfigColumn::name));

        for (var entry : columnMap.entrySet()) {
            var v = entry.getValue();
            if (v.size() > 1) { // 具有多个相同的 columnName 值
                throw new IllegalArgumentException("重复的 columnName !!! Class -> " + clazz.getName() + ", Field -> " + v.stream().map(c -> c.javaField().name()).toList());
            }
        }
    }

    private static Key[] initKeys(String tableName, AnnotationConfigColumn[] columns) {
        var primaryColumns = Stream.of(columns)
            .filter(AnnotationConfigColumn::primary)
            .toList();

        if (primaryColumns.isEmpty()) {
            return new Key[0];
        }

        if (primaryColumns.size() > 1) {
            throw new IllegalArgumentException(
                "暂不支持复合主键 !!! table=" + tableName +
                    ", columns=" + primaryColumns.stream().map(AnnotationConfigColumn::name).toList()
            );
        }

        var column = primaryColumns.get(0);
        return new Key[]{
            new KeyDefinition()
                .setName("key_" + column.name())
                .setColumnName(column.name())
                .setPrimary(true)
        };
    }

    private static Index[] initIndexes(AnnotationConfigColumn[] columns) {
        var indexes = new ArrayList<Index>();

        for (var column : columns) {
            if (column.primary()) {
                continue; // 主键通常已隐含 unique + index
            }

            if (column.unique()) {
                indexes.add(new IndexDefinition()
                    .setName("unique_" + column.name())
                    .setColumnName(column.name())
                    .setUnique(true));
            } else if (column.index()) {
                indexes.add(new IndexDefinition()
                    .setName("index_" + column.name())
                    .setColumnName(column.name())
                    .setUnique(false));
            }
        }

        return indexes.toArray(Index[]::new);
    }

    private static Map<String, AnnotationConfigColumn> initAllColumnMap(AnnotationConfigColumn[] infos) {
        var map = new HashMap<String, AnnotationConfigColumn>();
        for (var info : infos) {
            map.put(info.name(), info);
        }
        // javaFieldName 的优先级大于 columnName 所以允许覆盖
        for (var info : infos) {
            map.put(info.javaField().name(), info);
        }
        return map;
    }

    @Override
    public Class<Entity> entityClass() {
        return this.entityClass;
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public AnnotationConfigColumn[] columns() {
        return this.columns;
    }

    @Override
    public AnnotationConfigColumn getColumn(String column) {
        return this.columnMap.get(column);
    }

    @Override
    public Key[] keys() {
        return keys;
    }

    @Override
    public Index[] indexes() {
        return indexes;
    }

}
