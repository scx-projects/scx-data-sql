package dev.scx.data.sql.test.entity;

import dev.scx.data.sql.annotation.Column;
import dev.scx.data.sql.annotation.Table;

import java.time.LocalDateTime;

@Table("test_wallet")
public class TestWallet {

    @Column(primary = true, autoIncrement = true)
    public Long id;

    @Column(unique = true, notNull = true)
    public String owner;

    @Column(defaultValue = "0")
    public Long money;

    @Column(columnName = "updated_at")
    public LocalDateTime updatedAt;

}
