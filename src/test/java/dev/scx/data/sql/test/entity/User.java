package dev.scx.data.sql.test.entity;

import dev.scx.data.sql.annotation.Column;
import dev.scx.data.sql.annotation.Table;

import java.time.LocalDateTime;

@Table("test_user")
public class User {

    @Column(primary = true, autoIncrement = true)
    public Long id;

    @Column(defaultValue = "0")
    public Long money;

    public String name;

    /// 创建时间
    @Column(notNull = true, defaultValue = "(NOW())", index = true)
    public LocalDateTime createdDate;

    /// 最后修改时间
    @Column(notNull = true, defaultValue = "(NOW())", onUpdate = "CURRENT_TIMESTAMP", index = true)
    public LocalDateTime updatedDate;

}
