package dev.scx.data.sql.test.entity;

import dev.scx.data.sql.annotation.Column;
import dev.scx.data.sql.annotation.Table;

import java.time.LocalDateTime;

@Table("test_order_line")
public class TestOrderLine {

    @Column(primary = true, autoIncrement = true)
    public Long id;

    @Column(columnName = "account_id", index = true)
    public Long accountId;

    @Column(columnName = "product_sku", index = true)
    public String productSku;

    public Integer quantity;

    public Long amount;

    public String state;

    public String note;

    @Column(columnName = "created_at")
    public LocalDateTime createdAt;

}
