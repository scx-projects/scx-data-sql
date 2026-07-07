package dev.scx.data.sql.test;

import dev.scx.data.LockMode;
import dev.scx.data.sql.SQLRepository;
import dev.scx.data.sql.test.entity.TestWallet;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static dev.scx.data.field_policy.FieldPolicyBuilder.exclude;
import static dev.scx.data.field_policy.FieldPolicyBuilder.include;
import static dev.scx.data.query.QueryBuilder.eq;
import static dev.scx.data.sql.test.SQLTestKit.recreateTable;
import static dev.scx.data.sql.test.SQLTestKit.sqlClient;
import static org.testng.Assert.assertEquals;

public class TransactionRepositoryTest {

    public static SQLRepository<TestWallet> walletRepository;

    static void main(String[] args) throws Exception {
        beforeTest();
        testTransactionCommit();
        testTransactionRollbackOnRuntimeException();
        testConcurrentTransferWithExclusiveLock();
    }

    @BeforeTest
    public static void beforeTest() throws SQLException {
        walletRepository = new SQLRepository<>(TestWallet.class, sqlClient);
        recreateTable(walletRepository);
    }

    @Test
    public static void testTransactionCommit() throws SQLException {
        walletRepository.clear();

        sqlClient.autoTransaction(() -> {
            walletRepository.add(wallet("alice", 1000L), exclude("id", "updatedAt").assignField("updatedAt", "CURRENT_TIMESTAMP"));
            walletRepository.update(
                include("money").assignField("updatedAt", "CURRENT_TIMESTAMP"),
                eq("owner", "alice")
            );
        });

        assertEquals(walletRepository.count(), 1L);
        assertEquals(walletRepository.findFirst(eq("owner", "alice")).money, Long.valueOf(1000L));
    }

    @Test
    public static void testTransactionRollbackOnRuntimeException() throws SQLException {
        walletRepository.clear();

        try {
            sqlClient.autoTransaction(() -> {
                walletRepository.add(wallet("rollback-user", 100L), exclude("id", "updatedAt").assignField("updatedAt", "CURRENT_TIMESTAMP"));
                throw new RuntimeException("mock rollback");
            });
            Assert.fail("事务内部抛异常后不应该提交");
        } catch (RuntimeException expected) {
            assertEquals(expected.getMessage(), "mock rollback");
        }

        assertEquals(walletRepository.count(), 0L);
        Assert.assertNull(walletRepository.findFirst(eq("owner", "rollback-user")));
    }

    @Test
    public static void testConcurrentTransferWithExclusiveLock() throws InterruptedException {
        walletRepository.clear();

        var aliceId = walletRepository.add(wallet("alice", 10000L), exclude("id", "updatedAt").assignField("updatedAt", "CURRENT_TIMESTAMP"));
        var bobId = walletRepository.add(wallet("bob", 0L), exclude("id", "updatedAt").assignField("updatedAt", "CURRENT_TIMESTAMP"));

        var threadCount = 40;
        var amount = 100L;
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(threadCount);
        var errorRef = new AtomicReference<Throwable>();
        var threads = new ArrayList<Thread>();

        for (var i = 0; i < threadCount; i = i + 1) {
            var thread = Thread.ofVirtual().start(() -> {
                try {
                    start.await();
                    transfer(aliceId, bobId, amount);
                } catch (Throwable e) {
                    errorRef.compareAndSet(null, e);
                } finally {
                    done.countDown();
                }
            });
            threads.add(thread);
        }

        start.countDown();
        done.await();

        if (errorRef.get() != null) {
            throw new AssertionError("并发转账中出现异常", errorRef.get());
        }

        var alice = walletRepository.findFirst(eq("id", aliceId));
        var bob = walletRepository.findFirst(eq("id", bobId));

        assertEquals(alice.money, Long.valueOf(10000L - threadCount * amount));
        assertEquals(bob.money, Long.valueOf(threadCount * amount));
    }

    public static void transfer(Long fromId, Long toId, Long amount) throws SQLException {
        sqlClient.autoTransaction(() -> {
            var from = walletRepository.findFirst(eq("id", fromId), LockMode.EXCLUSIVE);
            var to = walletRepository.findFirst(eq("id", toId), LockMode.EXCLUSIVE);

            if (from == null || to == null) {
                throw new RuntimeException("用户不存在");
            }
            if (from.money < amount) {
                throw new RuntimeException("余额不足");
            }

            from.money = from.money - amount;
            to.money = to.money + amount;

            walletRepository.update(from, include("money"), eq("id", fromId));
            walletRepository.update(to, include("money"), eq("id", toId));
        });
    }

    private static TestWallet wallet(String owner, Long money) {
        var wallet = new TestWallet();
        wallet.owner = owner;
        wallet.money = money;
        return wallet;
    }

}
