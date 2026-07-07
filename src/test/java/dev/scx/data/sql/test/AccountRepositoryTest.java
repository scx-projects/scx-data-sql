package dev.scx.data.sql.test;

import dev.scx.data.sql.SQLRepository;
import dev.scx.data.sql.test.entity.TestAccount;
import dev.scx.data.sql.test.entity.TestAccountStatus;
import dev.scx.data.sql.test.view.AccountView;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import static dev.scx.data.field_policy.FieldPolicyBuilder.exclude;
import static dev.scx.data.field_policy.FieldPolicyBuilder.include;
import static dev.scx.data.query.BuildControl.SKIP_IF_NULL;
import static dev.scx.data.query.QueryBuilder.eq;
import static dev.scx.data.query.QueryBuilder.ne;
import static dev.scx.data.sql.test.SQLTestKit.*;
import static org.testng.Assert.*;

public class AccountRepositoryTest {

    public static SQLRepository<TestAccount> accountRepository;

    static void main(String[] args) throws Exception {
        beforeTest();
        testAddFindEnumDateAndNoColumn();
        testMapKeyAndViewMapping();
        testUpdateNullAndEmptyWhereSafety();
        testFinderCountFirstAndPage();
    }

    @BeforeTest
    public static void beforeTest() throws SQLException {
        accountRepository = new SQLRepository<>(TestAccount.class, sqlClient);
        recreateTable(accountRepository);
    }

    @Test
    public static void testAddFindEnumDateAndNoColumn() {
        accountRepository.clear();

        var id = accountRepository.add(
            account("alice", "Alice", TestAccountStatus.ACTIVE, 1000L, 1, LocalDate.of(2000, 1, 2)),
            exclude("id", "createdAt").assignField("createdAt", "CURRENT_TIMESTAMP").ignoreNull(false)
        );

        assertEquals(id, Long.valueOf(1L));

        var saved = accountRepository.findFirst(eq("userName", "alice"));

        assertNotNull(saved);
        assertEquals(saved.id, Long.valueOf(1L));
        assertEquals(saved.userName, "alice");
        assertEquals(saved.nickName, "Alice");
        assertEquals(saved.status, TestAccountStatus.ACTIVE);
        assertEquals(saved.balance, Long.valueOf(1000L));
        assertEquals(saved.level, Integer.valueOf(1));
        assertEquals(saved.birthday, LocalDate.of(2000, 1, 2));
        assertNotNull(saved.createdAt);

        // @NoColumn 字段不能落库, 查回来应该为 null
        assertNull(saved.tempToken);
    }

    @Test
    public static void testMapKeyAndViewMapping() {
        accountRepository.clear();
        seedAccounts();

        var map = accountRepository
            .finder(eq("userName", "alice"), include("id", "userName", "balance", "status", "createdAt"))
            .firstMap();

        assertNotNull(map);

        // Map key 必须是 Java 字段名, 不是数据库列名
        Assert.assertTrue(map.containsKey("userName"), map.toString());
        Assert.assertTrue(map.containsKey("createdAt"), map.toString());
        Assert.assertFalse(map.containsKey("user_name"), map.toString());
        Assert.assertFalse(map.containsKey("created_at"), map.toString());
        Assert.assertFalse(map.containsKey("tempToken"), map.toString());

        assertEquals(map.get("userName"), "alice");
        assertEquals(asLong(map.get("balance")), 1000L);

        var view = accountRepository
            .finder(eq("userName", "alice"), include("id", "userName", "balance", "status"))
            .first(AccountView.class);

        assertNotNull(view);
        assertEquals(view.id, Long.valueOf(1L));
        assertEquals(view.userName, "alice");
        assertEquals(view.balance, Long.valueOf(1000L));
        assertEquals(view.status, TestAccountStatus.ACTIVE);
    }

    @Test
    public static void testUpdateNullAndEmptyWhereSafety() {
        accountRepository.clear();
        seedAccounts();

        var update = new TestAccount();
        update.nickName = null;

        var affected = accountRepository.update(
            update,
            include("nickName").ignoreNull(false),
            eq("userName", "alice")
        );

        assertEquals(affected, 1L);
        assertNull(accountRepository.findFirst(eq("userName", "alice")).nickName);

        var dangerous = new TestAccount();
        dangerous.status = TestAccountStatus.LOCKED;

        Assert.expectThrows(
            IllegalArgumentException.class,
            () -> accountRepository.update(
                dangerous,
                include("status"),
                eq("id", null, SKIP_IF_NULL)
            )
        );

        assertEquals(accountRepository.count(eq("status", TestAccountStatus.LOCKED)), 0L);
    }

    @Test
    public static void testFinderCountFirstAndPage() {
        accountRepository.clear();
        seedAccounts();

        assertEquals(accountRepository.count(), 3L);
        assertEquals(accountRepository.count(eq("status", TestAccountStatus.ACTIVE)), 2L);
        assertNull(accountRepository.findFirst(eq("userName", "not-exists")));
        assertNull(accountRepository.finder(eq("userName", "not-exists")).firstMap());

        var page = accountRepository.find(
            ne("id", null)
                .desc("balance")
                .offset(1)
                .limit(1)
        );

        assertEquals(page.size(), 1);
        assertEquals(page.get(0).userName, "bob");

        var count = accountRepository
            .finder(ne("id", null).offset(1).limit(1))
            .count();

        // Finder.count 明确应该忽略 offset / limit
        assertEquals(count, 3L);
    }

    public static List<Long> seedAccounts() {
        return accountRepository.add(
            List.of(
                account("alice", "Alice", TestAccountStatus.ACTIVE, 1000L, 1, LocalDate.of(2000, 1, 2)),
                account("bob", null, TestAccountStatus.ACTIVE, 500L, 2, LocalDate.of(1999, 5, 6)),
                account("charlie", "C", TestAccountStatus.DISABLED, 0L, 1, null)
            ),
            exclude("id", "createdAt").assignField("createdAt", "CURRENT_TIMESTAMP").ignoreNull(false)
        );
    }

    private static TestAccount account(String userName, String nickName, TestAccountStatus status, Long balance, Integer level, LocalDate birthday) {
        var a = new TestAccount();
        a.userName = userName;
        a.nickName = nickName;
        a.status = status;
        a.balance = balance;
        a.level = level;
        a.birthday = birthday;
        a.tempToken = "should-not-save";
        return a;
    }

}
