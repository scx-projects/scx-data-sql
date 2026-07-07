package dev.scx.data.sql.test.entity;

import dev.scx.data.sql.annotation.Column;
import dev.scx.data.sql.annotation.NoColumn;
import dev.scx.data.sql.annotation.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Table("test_account")
public class TestAccount {

    @Column(primary = true, autoIncrement = true)
    public Long id;

    @Column(columnName = "user_name", unique = true, notNull = true)
    public String userName;

    @Column(columnName = "nick_name")
    public String nickName;

    public TestAccountStatus status;

    public Long balance;

    public Integer level;

    public LocalDate birthday;

    @Column(columnName = "created_at")
    public LocalDateTime createdAt;

    @NoColumn
    public String tempToken;

}
