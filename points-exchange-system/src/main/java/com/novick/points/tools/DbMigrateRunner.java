package com.novick.points.tools;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.sql.DataSource;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;

@Profile("db-migrate")
@Component
public class DbMigrateRunner implements CommandLineRunner {

    private final Environment env;
    private final DataSource targetDataSource;

    public DbMigrateRunner(Environment env, DataSource targetDataSource) {
        this.env = env;
        this.targetDataSource = targetDataSource;
    }

    @Override
    public void run(String... args) throws Exception {
        boolean enabled = Boolean.parseBoolean(env.getProperty("migrate.enabled", "false"));
        if (!enabled) {
            throw new IllegalStateException("迁移工具未启用：请添加参数 --migrate.enabled=true 并设置 profile=db-migrate");
        }

        String sourceUrl = required("migrate.source.url");
        String sourceUsername = env.getProperty("migrate.source.username", "sa");
        String sourcePassword = env.getProperty("migrate.source.password", "");

        boolean allowOverwrite = Boolean.parseBoolean(env.getProperty("migrate.allowOverwrite", "false"));

        DataSource sourceDataSource = buildSourceDataSource(sourceUrl, sourceUsername, sourcePassword);
        JdbcTemplate sourceJdbc = new JdbcTemplate(sourceDataSource);
        JdbcTemplate targetJdbc = new JdbcTemplate(targetDataSource);

        List<String> tables = List.of("user_account", "reward_item", "exchange_order", "points_transaction");

        if (!allowOverwrite) {
            for (String table : tables) {
                Integer cnt = targetJdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
                if (cnt != null && cnt > 0) {
                    throw new IllegalStateException("目标库表 " + table + " 非空（count=" + cnt
                            + "），为避免误覆盖已中止。若确认覆盖，请加 --migrate.allowOverwrite=true");
                }
            }
        } else {
            for (String table : tables) {
                targetJdbc.execute("TRUNCATE TABLE " + table);
            }
        }

        for (String table : tables) {
            migrateTable(sourceJdbc, targetJdbc, table);
        }
    }

    private DataSource buildSourceDataSource(String url, String username, String password) {
        String normalized = url.trim().toLowerCase(Locale.ROOT);
        String driverClassName;
        if (normalized.startsWith("jdbc:h2:")) {
            driverClassName = "org.h2.Driver";
        } else if (normalized.startsWith("jdbc:mysql:")) {
            driverClassName = "com.mysql.cj.jdbc.Driver";
        } else {
            throw new IllegalArgumentException("不支持的 source url: " + url);
        }

        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName(driverClassName);
        return ds;
    }

    private void migrateTable(JdbcTemplate sourceJdbc, JdbcTemplate targetJdbc, String table) throws Exception {
        List<String> columns = readColumns(sourceJdbc.getDataSource(), table);
        if (columns.isEmpty()) {
            throw new IllegalStateException("无法读取表结构: " + table);
        }

        String insertSql = buildInsertSql(table, columns);

        int total = sourceJdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        if (total == 0) {
            return;
        }

        int batchSize = Integer.parseInt(env.getProperty("migrate.batchSize", "500"));
        List<Object[]> buffer = new ArrayList<>(batchSize);

        sourceJdbc.query("SELECT * FROM " + table + " ORDER BY id", rs -> {
            Object[] row = new Object[columns.size()];
            for (int i = 0; i < columns.size(); i++) {
                row[i] = rs.getObject(i + 1);
            }
            buffer.add(row);
            if (buffer.size() >= batchSize) {
                flushBatch(targetJdbc, insertSql, buffer);
                buffer.clear();
            }
        });

        if (!buffer.isEmpty()) {
            flushBatch(targetJdbc, insertSql, buffer);
            buffer.clear();
        }

        Long maxId = targetJdbc.queryForObject("SELECT COALESCE(MAX(id),0) FROM " + table, Long.class);
        long next = (maxId == null ? 1L : maxId + 1L);
        targetJdbc.execute("ALTER TABLE " + table + " AUTO_INCREMENT=" + next);
    }

    private void flushBatch(JdbcTemplate targetJdbc, String insertSql, List<Object[]> buffer) {
        List<Object[]> data = new ArrayList<>(buffer);
        targetJdbc.batchUpdate(insertSql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws java.sql.SQLException {
                Object[] row = data.get(i);
                for (int j = 0; j < row.length; j++) {
                    ps.setObject(j + 1, row[j]);
                }
            }

            @Override
            public int getBatchSize() {
                return data.size();
            }
        });
    }

    private List<String> readColumns(DataSource dataSource, String table) throws Exception {
        try (Connection conn = Objects.requireNonNull(dataSource).getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT * FROM " + table + " WHERE 1=0");
                ResultSet rs = ps.executeQuery()) {
            ResultSetMetaData md = rs.getMetaData();
            List<String> cols = new ArrayList<>();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                cols.add(md.getColumnName(i));
            }
            return cols;
        }
    }

    private String buildInsertSql(String table, List<String> columns) {
        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO ").append(table).append(" (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(columns.get(i));
        }
        sb.append(") VALUES (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("?");
        }
        sb.append(")");
        return sb.toString();
    }

    private String required(String key) {
        String value = env.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("缺少必要参数: --" + key + "=...");
        }
        return value.trim();
    }
}

