package dev.scx.data.sql.test;

import dev.scx.data.sql.SQLRepository;
import dev.scx.jdbc.spy.ScxJdbcSpy;
import dev.scx.jdbc.spy.listener.logging.LoggingDataSourceListener;
import dev.scx.jdbc.spy.listener.logging.PreparedStatementLogStyle;
import dev.scx.logging.ScxLoggerConfig;
import dev.scx.logging.ScxLogging;
import dev.scx.sql.JDBCConnectionInfo;
import dev.scx.sql.SQL;
import dev.scx.sql.SQLClient;
import org.testng.Assert;

import java.sql.SQLException;

import static java.lang.System.Logger.Level.DEBUG;

public final class SQLTestKit {

    public static final SQLClient sqlClient = initSQLClient();

    static {
        ScxLogging.config("ScxJdbcSpy", new ScxLoggerConfig(DEBUG, null, null));
    }

    private static SQLClient initSQLClient() {
        return SQLClient.of(
            new JDBCConnectionInfo(
                "jdbc:mysql://127.0.0.1:3306/scx",
                "root",
                "root",
                "allowMultiQueries=true",
                "rewriteBatchedStatements=true",
                "createDatabaseIfNotExist=true"
            ),
            ds -> ScxJdbcSpy.spy(ds, new LoggingDataSourceListener(PreparedStatementLogStyle.RENDERED_SQL))
        );
    }

    public static void recreateTable(SQLRepository<?> repository) throws SQLException {
        sqlClient.execute(SQL.sql("drop table if exists " + sqlClient.dialect().quoteIdentifier(repository.table().name())));
        var ddls = sqlClient.dialect().getCreateTableDDLs(repository.table());
        for (var ddl : ddls) {
            sqlClient.execute(SQL.sql(ddl));
        }
        repository.clear();
    }

    public static long asLong(Object value) {
        Assert.assertTrue(value instanceof Number, "value 不是 Number : " + value);
        return ((Number) value).longValue();
    }

}
