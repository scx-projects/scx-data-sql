package dev.scx.data.sql.test.entity;

import dev.scx.data.sql.annotation.Column;
import dev.scx.data.sql.annotation.Table;

@Table("test_product")
public class TestProduct {

    @Column(primary = true, autoIncrement = true)
    public Long id;

    @Column(unique = true, notNull = true)
    public String sku;

    public String name;

    public String category;

    public Long price;

    @Column(columnName = "stock_count")
    public Integer stockCount;

    public String color;

    public Boolean online;

}
