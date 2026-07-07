package dev.scx.data.sql.test.entity;

import dev.scx.data.sql.annotation.Column;
import dev.scx.data.sql.annotation.Table;

@Table("like")
public class TestKeywordRow {

    @Column(primary = true, autoIncrement = true)
    public Long id;

    @Column(columnName = "select")
    public String select;

    @Column(columnName = "from")
    public String from;

    @Column(columnName = "where")
    public String where;

    @Column(columnName = "order")
    public String order;

    @Column(columnName = "group")
    public String group;

    public String like;

    @Column(columnName = "desc")
    public String desc;

}
