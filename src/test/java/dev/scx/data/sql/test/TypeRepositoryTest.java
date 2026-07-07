package dev.scx.data.sql.test;

import dev.scx.data.sql.SQLRepository;
import dev.scx.data.sql.test.entity.TestTypeRow;
import dev.scx.data.sql.test.entity.TestTypeStatus;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.math.BigInteger;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static dev.scx.data.field_policy.FieldPolicyBuilder.exclude;
import static dev.scx.data.field_policy.FieldPolicyBuilder.include;
import static dev.scx.data.query.QueryBuilder.*;
import static dev.scx.data.sql.test.SQLTestKit.recreateTable;
import static dev.scx.data.sql.test.SQLTestKit.sqlClient;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TypeRepositoryTest {

    public static SQLRepository<TestTypeRow> typeRepository;

    static void main(String[] args) throws Exception {
        beforeTest();
        testJavaTypesRoundTrip();
        testNullTypesAndPartialUpdate();
        testTypeConditions();
    }

    @BeforeTest
    public static void beforeTest() throws SQLException {
        typeRepository = new SQLRepository<>(TestTypeRow.class, sqlClient);
        recreateTable(typeRepository);
    }

    @Test
    public static void testJavaTypesRoundTrip() {
        typeRepository.clear();

        var source = row(
            "hello",
            123,
            1234567890123L,
            12.5D,
            new BigInteger("100"),
            true,
            LocalDate.of(2026, 6, 12),
            LocalDateTime.of(2026, 6, 12, 10, 11, 12),
            TestTypeStatus.RUNNING,
            new byte[]{1, 2, 3, 4}
        );

        var id = typeRepository.add(source, exclude("id"));
        assertEquals(id, Long.valueOf(1L));

        var saved = typeRepository.findFirst(eq("id", id));
        assertNotNull(saved);
        assertEquals(saved.stringValue, "hello");
        assertEquals(saved.intValue, Integer.valueOf(123));
        assertEquals(saved.longValue, Long.valueOf(1234567890123L));
        assertEquals(saved.doubleValue, Double.valueOf(12.5D));
        assertEquals(saved.bigIntegerValue, new BigInteger("100"));
        assertEquals(saved.booleanValue, Boolean.TRUE);
        assertEquals(saved.localDateValue, LocalDate.of(2026, 6, 12));
        assertEquals(saved.localDateTimeValue, LocalDateTime.of(2026, 6, 12, 10, 11, 12));
        assertEquals(saved.enumValue, TestTypeStatus.RUNNING);
        assertEquals(saved.bytesValue, new byte[]{1, 2, 3, 4});
    }

    @Test
    public static void testNullTypesAndPartialUpdate() {
        typeRepository.clear();

        var id = typeRepository.add(row("nullable", null, null, null, null, null, null, null, null, null), exclude("id").ignoreNull(false));

        var saved = typeRepository.findFirst(eq("id", id));
        assertNotNull(saved);
        assertEquals(saved.stringValue, "nullable");
        Assert.assertNull(saved.intValue);
        Assert.assertNull(saved.bigIntegerValue);
        Assert.assertNull(saved.localDateTimeValue);
        Assert.assertNull(saved.enumValue);

        var update = new TestTypeRow();
        update.bigIntegerValue = new BigInteger("1000");
        update.booleanValue = false;
        update.enumValue = TestTypeStatus.DONE;

        var affected = typeRepository.update(update, include("bigIntegerValue", "booleanValue", "enumValue"), eq("id", id));
        assertEquals(affected, 1L);

        saved = typeRepository.findFirst(eq("id", id));
        assertEquals(saved.bigIntegerValue, new BigInteger("1000"));
        assertEquals(saved.booleanValue, Boolean.FALSE);
        assertEquals(saved.enumValue, TestTypeStatus.DONE);
    }

    @Test
    public static void testTypeConditions() {
        typeRepository.clear();

        typeRepository.add(
            List.of(
                row("a", 1, 10L, 1.1D, new BigInteger("10"), true, LocalDate.of(2026, 1, 1), LocalDateTime.of(2026, 1, 1, 1, 0), TestTypeStatus.NEW, new byte[]{1}),
                row("b", 2, 20L, 2.2D, new BigInteger("20"), false, LocalDate.of(2026, 2, 1), LocalDateTime.of(2026, 2, 1, 2, 0), TestTypeStatus.RUNNING, new byte[]{2}),
                row("c", 3, 30L, 3.3D, new BigInteger("30"), true, LocalDate.of(2026, 3, 1), LocalDateTime.of(2026, 3, 1, 3, 0), TestTypeStatus.DONE, new byte[]{3})
            ),
            exclude("id").ignoreNull(false)
        );

        assertEquals(typeRepository.count(eq("booleanValue", true)), 2L);
        assertEquals(typeRepository.count(eq("enumValue", TestTypeStatus.RUNNING)), 1L);
        assertEquals(typeRepository.count(between("localDateValue", LocalDate.of(2026, 1, 15), LocalDate.of(2026, 3, 1))), 2L);
        assertEquals(typeRepository.count(gt("bigIntegerValue", new BigInteger("15"))), 2L);
    }

    private static TestTypeRow row(String stringValue, Integer intValue, Long longValue, Double doubleValue, BigInteger bigIntegerValue, Boolean booleanValue, LocalDate localDateValue, LocalDateTime localDateTimeValue, TestTypeStatus enumValue, byte[] bytesValue) {
        var row = new TestTypeRow();
        row.stringValue = stringValue;
        row.intValue = intValue;
        row.longValue = longValue;
        row.doubleValue = doubleValue;
        row.bigIntegerValue = bigIntegerValue;
        row.booleanValue = booleanValue;
        row.localDateValue = localDateValue;
        row.localDateTimeValue = localDateTimeValue;
        row.enumValue = enumValue;
        row.bytesValue = bytesValue;
        return row;
    }

}
