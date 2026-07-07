package dev.scx.data.sql.test;

import dev.scx.data.sql.SQLRepository;
import dev.scx.data.sql.test.entity.TestKeywordRow;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.List;

import static dev.scx.data.field_policy.FieldPolicyBuilder.exclude;
import static dev.scx.data.field_policy.FieldPolicyBuilder.include;
import static dev.scx.data.query.QueryBuilder.*;
import static dev.scx.data.sql.test.SQLTestKit.recreateTable;
import static dev.scx.data.sql.test.SQLTestKit.sqlClient;
import static org.testng.Assert.assertEquals;

public class KeywordRepositoryTest {

    public static SQLRepository<TestKeywordRow> keywordRepository;

    static void main(String[] args) throws Exception {
        beforeTest();
        testAddFindUpdateKeywordColumns();
        testWhereOrderAndMapKeyForKeywordColumns();
    }

    @BeforeTest
    public static void beforeTest() throws SQLException {
        keywordRepository = new SQLRepository<>(TestKeywordRow.class, sqlClient);
        recreateTable(keywordRepository);
    }

    @Test
    public static void testAddFindUpdateKeywordColumns() {
        keywordRepository.clear();

        var id = keywordRepository.add(row("select-1", "from-1", "where-1", "order-1", "group-1", "like-1", "desc-1"), exclude("id"));
        assertEquals(id, Long.valueOf(1L));

        var saved = keywordRepository.findFirst(eq("select", "select-1"));
        Assert.assertNotNull(saved);
        assertEquals(saved.select, "select-1");
        assertEquals(saved.from, "from-1");
        assertEquals(saved.where, "where-1");
        assertEquals(saved.order, "order-1");
        assertEquals(saved.group, "group-1");
        assertEquals(saved.like, "like-1");
        assertEquals(saved.desc, "desc-1");

        var update = new TestKeywordRow();
        update.like = "like-updated";
        update.order = "order-updated";

        var affected = keywordRepository.update(update, include("like", "order"), eq("where", "where-1"));
        assertEquals(affected, 1L);

        saved = keywordRepository.findFirst(eq("id", id));
        assertEquals(saved.like, "like-updated");
        assertEquals(saved.order, "order-updated");
        assertEquals(saved.group, "group-1");
    }

    @Test
    public static void testWhereOrderAndMapKeyForKeywordColumns() {
        keywordRepository.clear();

        keywordRepository.add(
            List.of(
                row("s1", "f1", "w1", "o3", "g1", "aaa", "d1"),
                row("s2", "f2", "w2", "o1", "g2", "bbb", "d2"),
                row("s3", "f3", "w3", "o2", "g3", "abc", "d3")
            ),
            exclude("id")
        );

        assertEquals(keywordRepository.count(like("like", "a")), 2L);
        assertEquals(keywordRepository.count(eq("order", "o1")), 1L);
        assertEquals(keywordRepository.count(in("group", new Object[]{"g1", "g3"})), 2L);

        var list = keywordRepository.find(ne("id", null).asc("order"));
        assertEquals(list.size(), 3);
        assertEquals(list.get(0).order, "o1");
        assertEquals(list.get(1).order, "o2");
        assertEquals(list.get(2).order, "o3");

        var map = keywordRepository.finder(eq("select", "s1")).firstMap();
        Assert.assertNotNull(map);
        Assert.assertTrue(map.containsKey("select"), map.toString());
        Assert.assertTrue(map.containsKey("where"), map.toString());
        Assert.assertTrue(map.containsKey("order"), map.toString());
        Assert.assertTrue(map.containsKey("group"), map.toString());
        Assert.assertTrue(map.containsKey("like"), map.toString());
    }

    private static TestKeywordRow row(String select, String from, String where, String order, String group, String like, String desc) {
        var row = new TestKeywordRow();
        row.select = select;
        row.from = from;
        row.where = where;
        row.order = order;
        row.group = group;
        row.like = like;
        row.desc = desc;
        return row;
    }

}
