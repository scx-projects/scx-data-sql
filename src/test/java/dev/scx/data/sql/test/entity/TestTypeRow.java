package dev.scx.data.sql.test.entity;

import dev.scx.data.sql.annotation.Column;
import dev.scx.data.sql.annotation.Table;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Table("test_type_row")
public class TestTypeRow {

    @Column(primary = true, autoIncrement = true)
    public Long id;

    public String stringValue;

    public Integer intValue;

    public Long longValue;

    public Double doubleValue;

    public BigInteger bigIntegerValue;

    public Boolean booleanValue;

    public LocalDate localDateValue;

    public LocalDateTime localDateTimeValue;

    public TestTypeStatus enumValue;

    public byte[] bytesValue;

}
