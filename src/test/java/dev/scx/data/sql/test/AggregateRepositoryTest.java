package dev.scx.data.sql.test;

import dev.scx.data.sql.SQLRepository;
import dev.scx.data.sql.test.entity.TestOrderLine;
import dev.scx.data.sql.test.view.AccountSalesView;
import dev.scx.exception.ScxWrappedException;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static dev.scx.data.aggregation.AggregationBuilder.*;
import static dev.scx.data.field_policy.FieldPolicyBuilder.exclude;
import static dev.scx.data.query.BuildControl.USE_EXPRESSION;
import static dev.scx.data.query.QueryBuilder.*;
import static dev.scx.data.sql.test.SQLTestKit.*;
import static org.testng.Assert.assertEquals;

public class AggregateRepositoryTest {

    public static SQLRepository<TestOrderLine> orderLineRepository;

    static void main(String[] args) throws Exception {
        beforeTest();
        testAggregateFirstAliasMap();
        testGroupByHavingOrderLimit();
        testExpressionGroupByHavingFirstNullAndWrappedException();
        testAggregateDtoAndForEach();
        testAggregateFirstDtoAndForEachDto();
        testAggregateDefaultOverloadsEmptySelectAndConsumerWrappingVariants();
    }

    @BeforeTest
    public static void beforeTest() throws SQLException {
        orderLineRepository = new SQLRepository<>(TestOrderLine.class, sqlClient);
        recreateTable(orderLineRepository);
    }

    @Test
    public static void testAggregateFirstAliasMap() {
        orderLineRepository.clear();
        seedOrderLines();

        var result = orderLineRepository.aggregateFirst(
            eq("state", "PAID"),
            agg("totalAmount", "SUM(amount)")
                .agg("totalQuantity", "SUM(quantity)")
                .agg("lineCount", "COUNT(*)")
        );

        Assert.assertNotNull(result);

        // 聚合 alias 不在 EntityTable 中, 但 Map 仍然应该保留 alias
        Assert.assertTrue(result.containsKey("totalAmount"), result.toString());
        Assert.assertTrue(result.containsKey("totalQuantity"), result.toString());
        Assert.assertTrue(result.containsKey("lineCount"), result.toString());

        assertEquals(asLong(result.get("totalAmount")), 1150L);
        assertEquals(asLong(result.get("totalQuantity")), 4L);
        assertEquals(asLong(result.get("lineCount")), 3L);
    }

    @Test
    public static void testGroupByHavingOrderLimit() {
        orderLineRepository.clear();
        seedOrderLines();

        var list = orderLineRepository.aggregate(
            eq("state", "PAID"),
            groupBy("accountId")
                .agg("totalAmount", "SUM(amount)")
                .agg("totalQuantity", "SUM(quantity)"),
            gt("totalAmount", 200L, USE_EXPRESSION)
                .desc("totalAmount", USE_EXPRESSION)
                .limit(2)
        );

        assertEquals(list.size(), 2);

        var first = list.get(0);
        var second = list.get(1);

        assertEquals(asLong(first.get("accountId")), 2L);
        assertEquals(asLong(first.get("totalAmount")), 900L);
        assertEquals(asLong(first.get("totalQuantity")), 1L);

        assertEquals(asLong(second.get("accountId")), 1L);
        assertEquals(asLong(second.get("totalAmount")), 250L);
        assertEquals(asLong(second.get("totalQuantity")), 3L);

        var byAccountId = list.stream().collect(Collectors.toMap(
            row -> asLong(row.get("accountId")),
            row -> asLong(row.get("totalAmount"))
        ));

        assertEquals(byAccountId.get(1L), Long.valueOf(250L));
        assertEquals(byAccountId.get(2L), Long.valueOf(900L));
    }


    @Test
    public static void testExpressionGroupByHavingFirstNullAndWrappedException() {
        orderLineRepository.clear();
        seedOrderLines();

        var grouped = orderLineRepository.aggregate(
            groupBy("paidFlag", "CASE WHEN state = 'PAID' THEN 'paid' ELSE 'other' END")
                .agg("lineCount", "COUNT(*)")
                .agg("totalAmount", "SUM(amount)"),
            gt("lineCount", 0L, USE_EXPRESSION).asc("paidFlag", USE_EXPRESSION)
        );

        assertEquals(grouped.size(), 2);
        assertEquals(grouped.get(0).get("paidFlag"), "other");
        assertEquals(asLong(grouped.get(0).get("lineCount")), 2L);
        assertEquals(asLong(grouped.get(0).get("totalAmount")), 460L);
        assertEquals(grouped.get(1).get("paidFlag"), "paid");
        assertEquals(asLong(grouped.get(1).get("lineCount")), 3L);
        assertEquals(asLong(grouped.get(1).get("totalAmount")), 1150L);

        var first = orderLineRepository
            .aggregator(
                eq("state", "PAID"),
                groupBy("accountId")
                    .agg("totalAmount", "SUM(amount)")
                    .agg("totalQuantity", "SUM(quantity)"),
                desc("totalAmount", USE_EXPRESSION).offset(99).limit(99)
            )
            .first(AccountSalesView.class);

        Assert.assertNotNull(first);
        assertEquals(first.accountId, Long.valueOf(2L));
        assertEquals(first.totalAmount, Long.valueOf(900L));
        assertEquals(first.totalQuantity, Long.valueOf(1L));

        var empty = orderLineRepository
            .aggregator(
                eq("state", "NOT_EXISTS"),
                groupBy("accountId").agg("totalAmount", "SUM(amount)")
            )
            .first();

        Assert.assertNull(empty);

        var error = Assert.expectThrows(
            ScxWrappedException.class,
            () -> orderLineRepository
                .aggregator(
                    eq("state", "PAID"),
                    groupBy("accountId").agg("totalAmount", "SUM(amount)"),
                    asc("accountId")
                )
                .forEach(row -> {
                    throw new IllegalStateException("aggregate-consumer-boom");
                })
        );
        Assert.assertTrue(error.getCause() instanceof IllegalStateException, String.valueOf(error.getCause()));
        assertEquals(error.getCause().getMessage(), "aggregate-consumer-boom");
    }

    @Test
    public static void testAggregateDtoAndForEach() {
        orderLineRepository.clear();
        seedOrderLines();

        var list = orderLineRepository
            .aggregator(
                eq("state", "PAID"),
                groupBy("accountId")
                    .agg("totalAmount", "SUM(amount)")
                    .agg("totalQuantity", "SUM(quantity)"),
                asc("accountId")
            )
            .list(AccountSalesView.class);

        assertEquals(list.size(), 2);
        assertEquals(list.get(0).accountId, Long.valueOf(1L));
        assertEquals(list.get(0).totalAmount, Long.valueOf(250L));
        assertEquals(list.get(0).totalQuantity, Long.valueOf(3L));
        assertEquals(list.get(1).accountId, Long.valueOf(2L));
        assertEquals(list.get(1).totalAmount, Long.valueOf(900L));
        assertEquals(list.get(1).totalQuantity, Long.valueOf(1L));

        var count = new AtomicInteger();

        orderLineRepository
            .aggregator(
                eq("state", "PAID"),
                groupBy("accountId").agg("totalAmount", "SUM(amount)"),
                asc("account_id", USE_EXPRESSION)
            )
            .forEach(row -> {
                Assert.assertTrue(row.containsKey("accountId"), row.toString());
                Assert.assertTrue(row.containsKey("totalAmount"), row.toString());
                count.incrementAndGet();
            });

        assertEquals(count.get(), 2);
    }

    @Test
    public static void testAggregateFirstDtoAndForEachDto() {
        orderLineRepository.clear();
        seedOrderLines();

        var first = orderLineRepository
            .aggregator(
                eq("state", "PAID"),
                groupBy("accountId")
                    .agg("totalAmount", "SUM(amount)")
                    .agg("totalQuantity", "SUM(quantity)"),
                desc("totalAmount", USE_EXPRESSION)
            )
            .first(AccountSalesView.class);

        Assert.assertNotNull(first);
        assertEquals(first.accountId, Long.valueOf(2L));
        assertEquals(first.totalAmount, Long.valueOf(900L));
        assertEquals(first.totalQuantity, Long.valueOf(1L));

        var dtoCount = new AtomicInteger();
        orderLineRepository
            .aggregator(
                eq("state", "PAID"),
                groupBy("accountId")
                    .agg("totalAmount", "SUM(amount)")
                    .agg("totalQuantity", "SUM(quantity)"),
                asc("accountId")
            )
            .forEach(row -> {
                Assert.assertNotNull(row.accountId);
                Assert.assertNotNull(row.totalAmount);
                Assert.assertNotNull(row.totalQuantity);
                dtoCount.incrementAndGet();
            }, AccountSalesView.class);

        assertEquals(dtoCount.get(), 2);
    }

    @Test
    public static void testAggregateDefaultOverloadsEmptySelectAndConsumerWrappingVariants() {
        orderLineRepository.clear();
        seedOrderLines();

        var allStates = orderLineRepository.aggregate(
            groupBy("state")
                .agg("lineCount", "COUNT(*)"),
            asc("state")
        );
        assertEquals(allStates.size(), 3);

        var paidOnly = orderLineRepository.aggregate(
            eq("state", "PAID"),
            agg("lineCount", "COUNT(*)")
        );
        assertEquals(paidOnly.size(), 1);
        assertEquals(asLong(paidOnly.get(0).get("lineCount")), 3L);

        var total = orderLineRepository.aggregateFirst(agg("lineCount", "COUNT(*)"));
        Assert.assertNotNull(total);
        assertEquals(asLong(total.get("lineCount")), 5L);

        var havingFirst = orderLineRepository.aggregateFirst(
            groupBy("accountId").agg("totalAmount", "SUM(amount)"),
            gt("totalAmount", 800L, USE_EXPRESSION)
        );
        Assert.assertNotNull(havingFirst);
        assertEquals(asLong(havingFirst.get("accountId")), 2L);
        assertEquals(asLong(havingFirst.get("totalAmount")), 960L);

        Assert.assertNull(orderLineRepository
            .aggregator(
                eq("state", "NOT_EXISTS"),
                groupBy("accountId").agg("totalAmount", "SUM(amount)")
            )
            .first(AccountSalesView.class));

        Assert.expectThrows(
            IllegalArgumentException.class,
            () -> orderLineRepository.aggregate(aggregation())
        );

        var typedError = Assert.expectThrows(
            ScxWrappedException.class,
            () -> orderLineRepository
                .aggregator(
                    eq("state", "PAID"),
                    groupBy("accountId").agg("totalAmount", "SUM(amount)"),
                    asc("accountId")
                )
                .forEach(row -> {
                    throw new IllegalArgumentException("aggregate-typed-consumer-boom");
                }, AccountSalesView.class)
        );
        Assert.assertTrue(typedError.getCause() instanceof IllegalArgumentException, String.valueOf(typedError.getCause()));
        assertEquals(typedError.getCause().getMessage(), "aggregate-typed-consumer-boom");
    }

    public static List<Long> seedOrderLines() {
        return orderLineRepository.add(
            List.of(
                orderLine(1L, "sku-a", 2, 200L, "PAID", "fast"),
                orderLine(1L, "sku-b", 1, 50L, "PAID", null),
                orderLine(2L, "sku-e", 1, 900L, "PAID", "vip"),
                orderLine(2L, "sku-d", 3, 60L, "CREATED", null),
                orderLine(3L, "sku-c", 5, 400L, "CANCELLED", "cancelled")
            ),
            exclude("id", "createdAt").assignField("createdAt", "CURRENT_TIMESTAMP").ignoreNull(false)
        );
    }

    private static TestOrderLine orderLine(Long accountId, String productSku, Integer quantity, Long amount, String state, String note) {
        var o = new TestOrderLine();
        o.accountId = accountId;
        o.productSku = productSku;
        o.quantity = quantity;
        o.amount = amount;
        o.state = state;
        o.note = note;
        return o;
    }

}
