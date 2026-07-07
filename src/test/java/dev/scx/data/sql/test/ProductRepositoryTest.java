package dev.scx.data.sql.test;

import dev.scx.data.LockMode;
import dev.scx.data.exception.DataAccessException;
import dev.scx.data.sql.SQLRepository;
import dev.scx.data.sql.exception.WrongConditionParamTypeException;
import dev.scx.data.sql.exception.WrongConditionTypeParamSizeException;
import dev.scx.data.sql.schema_mapping.AnnotationConfigTable;
import dev.scx.data.sql.test.entity.TestProduct;
import dev.scx.data.sql.test.view.ProductSummaryView;
import dev.scx.exception.ScxWrappedException;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.scx.data.aggregation.AggregationBuilder.agg;
import static dev.scx.data.field_policy.FieldPolicyBuilder.*;
import static dev.scx.data.query.BuildControl.*;
import static dev.scx.data.query.QueryBuilder.*;
import static dev.scx.data.sql.test.SQLTestKit.*;
import static dev.scx.sql.SQL.sql;
import static org.testng.Assert.*;

public class ProductRepositoryTest {

    public static SQLRepository<TestProduct> productRepository;

    static void main(String[] args) throws Exception {
        beforeTest();
        testBatchAddAndBasicConditions();
        testRepositoryDefaultOverloadsAndNullBatchMember();
        testExpressionOnlyAddWithDefaultOverload();
        testComplexWhereOrderLimitOffsetAndCount();
        testSkipControlsAndCollectionInConditions();
        testRawSqlSubQueryPrimitiveArraysExpressionValuesAndParserFailures();
        testLockableRepositoryFinders();
        testDefaultFinderEntrypointsAndNullHeavyConditions();
        testEntityTableConstructorRepositoryBehavior();
        testClearActuallyRemovesRows();
        testFieldPolicyVirtualFieldAndMapAlias();
        testVirtualFieldShadowsRealFieldAndPerFieldIgnoreNull();
        testUpdateExpressionNullAndSetSafety();
        testDeleteSafetyAndLimit();
        testForEachAndForEachMap();
        testFinderListMapListClassForEachClassAndWrappedException();
        testConsumerExceptionWrappingVariantsAndSqlExceptionWrapping();
    }

    @BeforeTest
    public static void beforeTest() throws SQLException {
        productRepository = new SQLRepository<>(TestProduct.class, sqlClient);
        recreateTable(productRepository);
    }

    @Test
    public static void testBatchAddAndBasicConditions() {
        productRepository.clear();
        var ids = seedProducts();

        assertEquals(ids, List.of(1L, 2L, 3L, 4L, 5L));
        assertEquals(productRepository.count(), 5L);

        assertEquals(productRepository.count(eq("color", null)), 1L);
        assertEquals(productRepository.count(ne("color", null)), 4L);

        assertEquals(productRepository.count(in("color", new Object[]{"black", null})), 3L);
        assertEquals(productRepository.count(notIn("color", new Object[]{"black", null})), 2L);

        assertEquals(productRepository.count(in("color", new Object[]{})), 0L);
        assertEquals(productRepository.count(notIn("color", new Object[]{})), 5L);

        assertEquals(productRepository.count(like("category", "hard")), 3L);
        assertEquals(productRepository.count(notLike("category", "hard")), 2L);
        assertEquals(productRepository.count(between("price", 50L, 100L)), 3L);
        assertEquals(productRepository.count(notBetween("price", 50L, 100L)), 2L);
    }

    @Test
    public static void testRepositoryDefaultOverloadsAndNullBatchMember() {
        productRepository.clear();

        var product = product("sku-default-one", "default-one", "hardware", 11L, 1, null, true);
        assertEquals(productRepository.add(product), Long.valueOf(1L));

        var batchIds = productRepository.add(List.of(
            product("sku-default-two", "default-two", "book", 22L, 2, "white", true),
            product("sku-default-three", "default-three", "food", 33L, 3, "brown", false)
        ));
        assertEquals(batchIds, List.of(2L, 3L));

        var expressionBatchIds = productRepository.add(
            java.util.Arrays.asList((TestProduct) null, (TestProduct) null),
            excludeAll()
                .assignField("sku", "CONCAT('sku-null-batch-', UUID())")
                .assignField("name", "'null-batch-product'")
                .assignField("category", "'generated'")
                .assignField("price", "44")
                .assignField("stockCount", "4")
                .assignField("online", "true")
        );
        assertEquals(expressionBatchIds, List.of(4L, 5L));

        assertEquals(productRepository.count(), 5L);

        var onlySku = productRepository.find(include("sku"));
        assertEquals(onlySku.size(), 5);
        assertNotNull(onlySku.get(0).sku);
        assertNull(onlySku.get(0).name);

        var firstWithPolicy = productRepository.findFirst(eq("sku", "sku-default-one"), include("sku"));
        assertNotNull(firstWithPolicy);
        assertEquals(firstWithPolicy.sku, "sku-default-one");
        assertNull(firstWithPolicy.name);

        var update = new TestProduct();
        update.name = "default-one-updated";
        assertEquals(productRepository.update(update, eq("sku", "sku-default-one")), 1L);
        assertEquals(productRepository.findFirst(eq("sku", "sku-default-one")).name, "default-one-updated");

        assertEquals(
            productRepository.update(
                excludeAll().assignField("stockCount", "stock_count + 10"),
                eq("sku", "sku-default-one")
            ),
            1L
        );
        assertEquals(productRepository.findFirst(eq("sku", "sku-default-one")).stockCount, Integer.valueOf(11));
    }

    @Test
    public static void testExpressionOnlyAddWithDefaultOverload() {
        productRepository.clear();

        var id = productRepository.add(
            excludeAll()
                .assignField("sku", "'sku-expr'")
                .assignField("name", "'generated-product'")
                .assignField("category", "'generated'")
                .assignField("price", "321")
                .assignField("stockCount", "7")
                .assignField("online", "true")
        );

        assertEquals(id, Long.valueOf(1L));

        var saved = productRepository.findFirst(eq("sku", "sku-expr"));
        assertNotNull(saved);
        assertEquals(saved.name, "generated-product");
        assertEquals(saved.category, "generated");
        assertEquals(saved.price, Long.valueOf(321L));
        assertEquals(saved.stockCount, Integer.valueOf(7));
        assertEquals(saved.online, Boolean.TRUE);
    }

    @Test
    public static void testComplexWhereOrderLimitOffsetAndCount() {
        productRepository.clear();
        seedProducts();

        var complexWhere = and(
            or(eq("category", "hardware"), eq("category", "book")),
            gt("stockCount", 0),
            not(eq("online", false))
        );

        assertEquals(productRepository.count(complexWhere), 3L);

        var page = productRepository.find(
            gt("price", 0L)
                .desc("price")
                .offset(1)
                .limit(2)
        );

        assertEquals(page.size(), 2);
        assertEquals(page.get(0).sku, "sku-a");
        assertEquals(page.get(1).sku, "sku-c");

        var count = productRepository
            .finder(gt("price", 0L).offset(1).limit(1))
            .count();

        assertEquals(count, 5L);
    }

    @Test
    public static void testSkipControlsAndCollectionInConditions() {
        productRepository.clear();
        seedProducts();

        assertEquals(productRepository.count(and(eq("category", "hardware"), eq("sku", null, SKIP_IF_NULL))), 3L);
        assertEquals(productRepository.count(and(eq("category", "hardware"), eq("sku", "", SKIP_IF_EMPTY_STRING))), 3L);
        assertEquals(productRepository.count(and(eq("category", "hardware"), eq("sku", "   ", SKIP_IF_BLANK_STRING))), 3L);
        assertEquals(productRepository.count(and(eq("category", "hardware"), in("sku", List.of(), SKIP_IF_EMPTY_LIST))), 3L);
        assertEquals(productRepository.count(and(eq("category", "hardware"), in("sku", new Object[0], SKIP_IF_EMPTY_LIST))), 3L);

        assertEquals(productRepository.count(in("sku", List.of("sku-a", "sku-c", "sku-c"))), 2L);
        assertEquals(productRepository.count(notIn("sku", List.of("sku-a", "sku-c"))), 3L);
        assertEquals(productRepository.count(between("price", null, 100L, SKIP_IF_NULL)), 5L);

        expectThrows(WrongConditionTypeParamSizeException.class, () -> productRepository.count(like("name", null)));
        expectThrows(WrongConditionTypeParamSizeException.class, () -> productRepository.count(notLike("name", null)));
        expectThrows(WrongConditionTypeParamSizeException.class, () -> productRepository.count(in("sku", null)));
    }


    @Test
    public static void testRawSqlSubQueryPrimitiveArraysExpressionValuesAndParserFailures() {
        productRepository.clear();
        seedProducts();

        assertEquals(productRepository.count(whereClause("price >= ? AND price < ?", 50L, 900L)), 3L);
        assertEquals(productRepository.count(lt("price", sql("?", 100L))), 3L);
        assertEquals(productRepository.count(like("name", sql("?", "key"))), 1L);
        assertEquals(productRepository.count(likeRegex("name", "%key%")), 1L);
        assertEquals(productRepository.count(notLikeRegex("name", "%key%")), 4L);
        assertEquals(productRepository.count(in("stockCount", new int[]{10, 30, 30})), 2L);
        assertEquals(productRepository.count(in("sku", sql("select sku from test_product where price >= ?", 100L))), 2L);
        assertEquals(productRepository.count(in("sku", "select sku from test_product where online = 1", USE_EXPRESSION_VALUE)), 4L);
        assertEquals(productRepository.count(eq("LOWER(category)", "hardware", USE_EXPRESSION)), 3L);
        assertEquals(productRepository.count(eq("category", "LOWER('HARDWARE')", USE_EXPRESSION_VALUE)), 3L);
        assertEquals(productRepository.count(between("price", sql("?", 50L), sql("?", 100L))), 3L);
        assertEquals(productRepository.count(and(eq("color", null), not(eq("online", false)))), 1L);
        assertEquals(productRepository.count(or(eq("sku", "sku-a"), and(gte("price", 80L), lte("price", 100L)))), 2L);

        expectThrows(IllegalArgumentException.class, () -> productRepository.count(eq("notExists", 1)));
        expectThrows(WrongConditionParamTypeException.class, () -> productRepository.count(in("sku", "not-array")));
        expectThrows(WrongConditionTypeParamSizeException.class, () -> productRepository.count(gt("price", null)));
        expectThrows(WrongConditionTypeParamSizeException.class, () -> productRepository.count(between("price", 1L, null)));
        expectThrows(IllegalArgumentException.class, () -> productRepository.find(excludeAll()));
    }

    @Test
    public static void testLockableRepositoryFinders() throws SQLException {
        productRepository.clear();
        seedProducts();

        sqlClient.autoTransaction(() -> {
            var shared = productRepository.finder(eq("sku", "sku-a"), LockMode.SHARED).first();
            assertNotNull(shared);
            assertEquals(shared.name, "keyboard");

            var exclusive = productRepository.findFirst(eq("sku", "sku-b"), LockMode.EXCLUSIVE);
            assertNotNull(exclusive);
            assertEquals(exclusive.name, "mouse");

            var lockedPage = productRepository.find(gt("price", 0L), include("sku"), LockMode.SHARED);
            assertEquals(lockedPage.size(), 5);
            assertNotNull(lockedPage.get(0).sku);
            assertNull(lockedPage.get(0).name);

            var lockedAll = productRepository.find(LockMode.SHARED);
            assertEquals(lockedAll.size(), 5);

            var lockedOnlySku = productRepository.find(include("sku"), LockMode.EXCLUSIVE);
            assertEquals(lockedOnlySku.size(), 5);
            assertNotNull(lockedOnlySku.get(0).sku);
            assertNull(lockedOnlySku.get(0).name);

            var lockedFirstWithPolicy = productRepository.findFirst(eq("sku", "sku-c"), include("sku"), LockMode.SHARED);
            assertNotNull(lockedFirstWithPolicy);
            assertEquals(lockedFirstWithPolicy.sku, "sku-c");
            assertNull(lockedFirstWithPolicy.name);
        });
    }

    @Test
    public static void testDefaultFinderEntrypointsAndNullHeavyConditions() throws SQLException {
        productRepository.clear();
        seedProducts();

        assertEquals(productRepository.finder().count(), 5L);

        var defaultPolicyFinder = productRepository.finder(include("sku"));
        var onlySku = defaultPolicyFinder.first();
        assertNotNull(onlySku);
        assertNotNull(onlySku.sku);
        assertNull(onlySku.name);

        sqlClient.autoTransaction(() -> {
            var sharedOnlySku = productRepository.finder(include("sku"), LockMode.SHARED).first();
            assertNotNull(sharedOnlySku);
            assertNotNull(sharedOnlySku.sku);
            assertNull(sharedOnlySku.name);

            var exclusiveAny = productRepository.finder(LockMode.EXCLUSIVE).first();
            assertNotNull(exclusiveAny);
            assertNotNull(exclusiveAny.id);
        });

        assertEquals(productRepository.count(in("color", new Object[]{null, null})), 1L);
        assertEquals(productRepository.count(notIn("color", new Object[]{null, null})), 4L);
        assertEquals(productRepository.count(in("stockCount", new long[]{10L, 30L, 30L})), 2L);
        assertEquals(productRepository.count(in("online", new boolean[]{true})), 4L);
        assertEquals(productRepository.count(notBetween("price", sql("?", 50L), sql("?", 100L))), 2L);
    }

    @Test
    public static void testEntityTableConstructorRepositoryBehavior() throws SQLException {
        var table = new AnnotationConfigTable<>(TestProduct.class);
        var repository = new SQLRepository<>(table, sqlClient);

        recreateTable(repository);

        Assert.assertSame(repository.entityClass(), TestProduct.class);
        Assert.assertSame(repository.table(), table);
        Assert.assertSame(repository.sqlClient(), sqlClient);
        assertNotNull(repository.beanBuilder());
        assertNotNull(repository.entityListExtractor());
        assertNotNull(repository.entityExtractor());

        var id = repository.add(
            product("sku-table-ctor", "table-ctor", "hardware", 77L, 7, null, true),
            exclude("id").ignoreNull(false)
        );
        assertEquals(id, Long.valueOf(1L));

        var selected = repository.finder(eq("sku", "sku-table-ctor"), include("sku", "name", "stockCount")).first();
        assertNotNull(selected);
        assertEquals(selected.sku, "sku-table-ctor");
        assertEquals(selected.name, "table-ctor");
        assertEquals(selected.stockCount, Integer.valueOf(7));
        assertNull(selected.category);

        var asMap = repository.finder(eq("sku", "sku-table-ctor")).firstMap();
        assertEquals(asMap.get("sku"), "sku-table-ctor");
        assertEquals(asLong(asMap.get("stockCount")), 7L);
        Assert.assertFalse(asMap.containsKey("stock_count"), asMap.toString());

        var summary = repository.finder(eq("sku", "sku-table-ctor"), include("sku", "name", "stockCount")).first(ProductSummaryView.class);
        assertNotNull(summary);
        assertEquals(summary.sku, "sku-table-ctor");
        assertEquals(summary.stockCount, Integer.valueOf(7));

        var update = new TestProduct();
        update.price = 99L;
        assertEquals(repository.update(update, include("price"), eq("sku", "sku-table-ctor")), 1L);
        assertEquals(repository.findFirst(eq("sku", "sku-table-ctor")).price, Long.valueOf(99L));

        var aggregate = repository.aggregateFirst(
            eq("online", true),
            agg("totalPrice", "SUM(price)").agg("productCount", "COUNT(*)")
        );
        assertNotNull(aggregate);
        assertEquals(asLong(aggregate.get("totalPrice")), 99L);
        assertEquals(asLong(aggregate.get("productCount")), 1L);

        assertEquals(repository.delete(eq("sku", "sku-table-ctor")), 1L);
        assertEquals(repository.count(), 0L);
    }

    @Test
    public static void testClearActuallyRemovesRows() {
        productRepository.clear();
        seedProducts();
        assertEquals(productRepository.count(), 5L);

        productRepository.clear();

        assertEquals(productRepository.count(), 0L);
        assertNull(productRepository.findFirst(eq("sku", "sku-a")));
    }

    @Test
    public static void testFieldPolicyVirtualFieldAndMapAlias() {
        productRepository.clear();
        seedProducts();

        var product = productRepository.findFirst(
            eq("sku", "sku-a"),
            include("id", "sku", "name")
        );

        assertNotNull(product);
        assertEquals(product.sku, "sku-a");
        assertEquals(product.name, "keyboard");
        assertNull(product.category);
        assertNull(product.price);
        assertNull(product.stockCount);
        assertNull(product.color);
        assertNull(product.online);

        var map = productRepository
            .finder(
                eq("sku", "sku-a"),
                include("id", "sku")
                    .virtualField("displayName", "CONCAT(category, ':', name)")
                    .virtualField("doublePrice", "price * 2")
            )
            .firstMap();

        assertNotNull(map);

        // 虚拟字段 alias 不在 EntityTable 中, 但 Map 仍然应该保留原始 alias
        Assert.assertTrue(map.containsKey("displayName"), map.toString());
        Assert.assertTrue(map.containsKey("doublePrice"), map.toString());

        assertEquals(map.get("displayName"), "hardware:keyboard");
        assertEquals(asLong(map.get("doublePrice")), 200L);

        var normalMap = productRepository.finder(eq("sku", "sku-a")).firstMap();
        Assert.assertTrue(normalMap.containsKey("stockCount"), normalMap.toString());
        Assert.assertFalse(normalMap.containsKey("stock_count"), normalMap.toString());
    }

    @Test
    public static void testVirtualFieldShadowsRealFieldAndPerFieldIgnoreNull() {
        productRepository.clear();
        seedProducts();

        var row = productRepository
            .finder(
                eq("sku", "sku-a"),
                include("sku", "name").virtualField("name", "UPPER(name)")
            )
            .firstMap();

        assertNotNull(row);
        assertEquals(row.get("sku"), "sku-a");
        assertEquals(row.get("name"), "KEYBOARD");

        var update = new TestProduct();
        update.name = null;
        update.color = null;

        var affected = productRepository.update(
            update,
            include("name", "color").ignoreNull(true).ignoreNull("color", false),
            eq("sku", "sku-a")
        );

        assertEquals(affected, 1L);

        var saved = productRepository.findFirst(eq("sku", "sku-a"));
        assertEquals(saved.name, "keyboard");
        assertNull(saved.color);

        var mixedUpdate = new TestProduct();
        mixedUpdate.stockCount = 1;
        affected = productRepository.update(
            mixedUpdate,
            include("stockCount").assignField("stockCount", "stock_count + 2"),
            eq("sku", "sku-a")
        );

        assertEquals(affected, 1L);
        assertEquals(productRepository.findFirst(eq("sku", "sku-a")).stockCount, Integer.valueOf(12));
    }

    @Test
    public static void testUpdateExpressionNullAndSetSafety() {
        productRepository.clear();
        seedProducts();

        var update = new TestProduct();
        update.name = "mechanical-keyboard";
        update.price = 999L;
        update.category = "should-not-update";

        var affected = productRepository.update(
            update,
            include("name", "price"),
            eq("sku", "sku-a")
        );

        assertEquals(affected, 1L);

        var saved = productRepository.findFirst(eq("sku", "sku-a"));
        assertEquals(saved.name, "mechanical-keyboard");
        assertEquals(saved.price, Long.valueOf(999L));
        assertEquals(saved.category, "hardware");

        var nullUpdate = new TestProduct();
        nullUpdate.color = null;

        affected = productRepository.update(
            nullUpdate,
            include("color").ignoreNull(false),
            eq("sku", "sku-a")
        );

        assertEquals(affected, 1L);
        assertNull(productRepository.findFirst(eq("sku", "sku-a")).color);

        affected = productRepository.update(
            excludeAll().assignField("stockCount", "stock_count + 5"),
            eq("sku", "sku-a")
        );

        assertEquals(affected, 1L);
        assertEquals(productRepository.findFirst(eq("sku", "sku-a")).stockCount, Integer.valueOf(15));

        var dangerous = new TestProduct();
        dangerous.name = "danger";

        expectThrows(
            IllegalArgumentException.class,
            () -> productRepository.update(
                dangerous,
                include("name"),
                eq("id", null, SKIP_IF_NULL)
            )
        );

        assertNull(productRepository.findFirst(eq("name", "danger")));

        expectThrows(
            IllegalArgumentException.class,
            () -> productRepository.update(
                new TestProduct(),
                excludeAll(),
                eq("id", 1L)
            )
        );
    }

    @Test
    public static void testDeleteSafetyAndLimit() {
        productRepository.clear();
        seedProducts();

        expectThrows(IllegalArgumentException.class, () -> productRepository.delete(query()));
        expectThrows(IllegalArgumentException.class, () -> productRepository.delete(eq("id", null, SKIP_IF_NULL)));

        var affected = productRepository.delete(
            gt("id", 0L)
                .asc("id")
                .limit(2)
        );

        assertEquals(affected, 2L);
        assertEquals(productRepository.count(), 3L);
        assertNull(productRepository.findFirst(eq("sku", "sku-a")));
        assertNull(productRepository.findFirst(eq("sku", "sku-b")));
        assertNotNull(productRepository.findFirst(eq("sku", "sku-c")));
    }

    @Test
    public static void testForEachAndForEachMap() {
        productRepository.clear();
        seedProducts();

        var count = new AtomicInteger();
        productRepository.finder(ne("id", null)).forEach(product -> {
            assertNotNull(product.id);
            count.incrementAndGet();
        });
        assertEquals(count.get(), 5);

        var mapCount = new AtomicInteger();
        productRepository.finder(ne("id", null)).forEachMap(row -> {
            Assert.assertTrue(row.containsKey("sku"), row.toString());
            Assert.assertTrue(row.containsKey("stockCount"), row.toString());
            mapCount.incrementAndGet();
        });
        assertEquals(mapCount.get(), 5);
    }

    @Test
    public static void testFinderListMapListClassForEachClassAndWrappedException() {
        productRepository.clear();
        seedProducts();

        var maps = productRepository
            .finder(ne("id", null).asc("sku"), include("sku", "stockCount"))
            .listMap();

        assertEquals(maps.size(), 5);
        Assert.assertTrue(maps.get(0).containsKey("sku"), maps.get(0).toString());
        Assert.assertTrue(maps.get(0).containsKey("stockCount"), maps.get(0).toString());
        Assert.assertFalse(maps.get(0).containsKey("stock_count"), maps.get(0).toString());
        assertEquals(maps.get(0).get("sku"), "sku-a");
        assertEquals(asLong(maps.get(0).get("stockCount")), 10L);

        var views = productRepository
            .finder(ne("id", null).asc("sku"), include("sku", "name", "stockCount"))
            .list(ProductSummaryView.class);

        assertEquals(views.size(), 5);
        assertEquals(views.get(0).sku, "sku-a");
        assertEquals(views.get(0).name, "keyboard");
        assertEquals(views.get(0).stockCount, Integer.valueOf(10));

        var typedCount = new AtomicInteger();
        productRepository
            .finder(ne("id", null).asc("sku"), include("sku", "name", "stockCount"))
            .forEach(view -> {
                assertNotNull(view.sku);
                assertNotNull(view.name);
                typedCount.incrementAndGet();
            }, ProductSummaryView.class);
        assertEquals(typedCount.get(), 5);

        var error = expectThrows(
            ScxWrappedException.class,
            () -> productRepository.finder(ne("id", null)).forEach(product -> {
                throw new IllegalStateException("consumer-boom");
            })
        );
        assertTrue(error.getCause() instanceof IllegalStateException, String.valueOf(error.getCause()));
        assertEquals(error.getCause().getMessage(), "consumer-boom");
    }

    @Test
    public static void testConsumerExceptionWrappingVariantsAndSqlExceptionWrapping() {
        productRepository.clear();
        seedProducts();

        Assert.assertNull(productRepository.finder(eq("sku", "not-exists")).first(ProductSummaryView.class));

        var typedError = expectThrows(
            ScxWrappedException.class,
            () -> productRepository
                .finder(ne("id", null), include("sku", "name", "stockCount"))
                .forEach(view -> {
                    throw new IllegalArgumentException("typed-consumer-boom");
                }, ProductSummaryView.class)
        );
        assertTrue(typedError.getCause() instanceof IllegalArgumentException, String.valueOf(typedError.getCause()));
        assertEquals(typedError.getCause().getMessage(), "typed-consumer-boom");

        var mapError = expectThrows(
            ScxWrappedException.class,
            () -> productRepository.finder(ne("id", null)).forEachMap(row -> {
                throw new IllegalStateException("map-consumer-boom");
            })
        );
        assertTrue(mapError.getCause() instanceof IllegalStateException, String.valueOf(mapError.getCause()));
        assertEquals(mapError.getCause().getMessage(), "map-consumer-boom");

        var duplicate = product("sku-a", "duplicate", "hardware", 1L, 1, "black", true);
        expectThrows(DataAccessException.class, () -> productRepository.add(duplicate, exclude("id")));
    }

    public static List<Long> seedProducts() {
        return productRepository.add(
            List.of(
                product("sku-a", "keyboard", "hardware", 100L, 10, "black", true),
                product("sku-b", "mouse", "hardware", 50L, 0, null, true),
                product("sku-c", "book-a", "book", 80L, 30, "white", true),
                product("sku-d", "coffee", "food", 20L, 100, "brown", false),
                product("sku-e", "monitor", "hardware", 900L, 5, "black", true)
            ),
            exclude("id").ignoreNull(false)
        );
    }

    private static TestProduct product(String sku, String name, String category, Long price, Integer stockCount, String color, Boolean online) {
        var p = new TestProduct();
        p.sku = sku;
        p.name = name;
        p.category = category;
        p.price = price;
        p.stockCount = stockCount;
        p.color = color;
        p.online = online;
        return p;
    }

}
