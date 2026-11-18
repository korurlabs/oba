package com.korur.oba.support;

// 1. Framework-level
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;

// 3. Java SDK
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * Throwaway databases for the tests that prove how a migration converts old data, which needs a
 * schema older than the one the application runs on. They live on the server of the application's
 * own data source, reached with its credentials, so they come from the same environment as
 * everything else and no URL is written in a test.
 *
 * The schema up to the starting version is migrated once, into a template database; every test
 * then gets a copy of that template, which PostgreSQL makes without running a migration again,
 * and drops it afterwards. The user of the data source must be allowed to create databases.
 */
public final class ScratchDatabases implements AutoCloseable {

    private static final String MIGRATIONS = "classpath:db/migration";

    private final String serverUrl;
    private final String query;
    private final String adminUrl;
    private final String username;
    private final String password;
    private final String template;

    private ScratchDatabases(DataSourceProperties dataSource) {
        String url = dataSource.determineUrl();
        int queryStart = url.indexOf('?');
        String withoutQuery = queryStart < 0 ? url : url.substring(0, queryStart);
        this.query = queryStart < 0 ? "" : url.substring(queryStart);
        this.serverUrl = withoutQuery.substring(0, withoutQuery.lastIndexOf('/') + 1);
        this.adminUrl = url;
        this.username = dataSource.determineUsername();
        this.password = dataSource.determinePassword();
        this.template = "oba_migration_template_" + UUID.randomUUID().toString().replace("-", "");
    }

    /** Creates the template database and migrates it to the given version. */
    public static ScratchDatabases migratedTo(DataSourceProperties dataSource, String version) throws SQLException {
        ScratchDatabases databases = new ScratchDatabases(dataSource);
        databases.execute("CREATE DATABASE " + databases.template);
        databases.migrate(databases.template, version);
        return databases;
    }

    /** A new database copied from the template; the caller drops it with {@link #drop}. */
    public String createCopy() throws SQLException {
        String name = "oba_migration_itest_" + UUID.randomUUID().toString().replace("-", "");
        execute("CREATE DATABASE " + name + " TEMPLATE " + template);
        return name;
    }

    public void drop(String name) throws SQLException {
        execute("DROP DATABASE IF EXISTS " + name + " WITH (FORCE)");
    }

    /** Applies the migrations of the application to the named database, up to the given version. */
    public void migrate(String name, String version) {
        Flyway.configure()
                .dataSource(urlOf(name), username, password)
                .locations(MIGRATIONS)
                .target(version)
                .load()
                .migrate();
    }

    public String urlOf(String name) {
        return serverUrl + name + query;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    @Override
    public void close() throws SQLException {
        drop(template);
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
