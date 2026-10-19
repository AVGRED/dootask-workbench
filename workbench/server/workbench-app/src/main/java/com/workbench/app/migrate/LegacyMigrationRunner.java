package com.workbench.app.migrate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 旧库数据迁移：把旧 dootask 库的同名表逐表搬到 v2 库。
 *
 * <p>启用方式：{@code --workbench.migrate.enabled=true --workbench.migrate.source-url=...}。
 * 默认 dry-run 只统计不写入；{@code --workbench.migrate.dry-run=false} 才真正写库。
 * 旧库缺少新列（如 deleted_at、milestone_id、owner_userid）时自动填默认值。</p>
 */
@Component
@ConditionalOnProperty(name = "workbench.migrate.enabled", havingValue = "true")
public class LegacyMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacyMigrationRunner.class);
    private static final int BATCH_SIZE = 500;

    private final JdbcTemplate target;
    private final String sourceUrl;
    private final String sourceUser;
    private final String sourcePassword;
    private final boolean dryRun;
    private final boolean exit;

    public LegacyMigrationRunner(
            JdbcTemplate target,
            @Value("${workbench.migrate.source-url:}") String sourceUrl,
            @Value("${workbench.migrate.source-username:}") String sourceUser,
            @Value("${workbench.migrate.source-password:}") String sourcePassword,
            @Value("${workbench.migrate.dry-run:true}") boolean dryRun,
            @Value("${workbench.migrate.exit:false}") boolean exit) {
        this.target = target;
        this.sourceUrl = sourceUrl;
        this.sourceUser = sourceUser;
        this.sourcePassword = sourcePassword;
        this.dryRun = dryRun;
        this.exit = exit;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            log.error("migration aborted: workbench.migrate.source-url is empty");
            finish(1);
            return;
        }
        List<MigrationPlan.TableStep> steps = MigrationPlan.steps();
        List<long[]> counts = new ArrayList<>();
        try (Connection source = DriverManager.getConnection(sourceUrl, sourceUser, sourcePassword)) {
            for (MigrationPlan.TableStep step : steps) {
                long[] result = migrateTable(source, step);
                counts.add(result);
                log.info("table {}: rows={} copied={}", step.targetTable(), result[0], result[1]);
            }
            System.out.println(MigrationPlan.formatSummary(steps, counts, dryRun));
            finish(0);
        } catch (SQLException exception) {
            log.error("migration failed", exception);
            finish(1);
        }
    }

    /** 返回 {源行数, 复制行数}。 */
    private long[] migrateTable(Connection source, MigrationPlan.TableStep step) throws SQLException {
        if (!tableExists(source, step.sourceTable())) {
            log.warn("skip {}: source table not found", step.sourceTable());
            return new long[]{0, 0};
        }
        Set<String> available = columnsOf(source, step.sourceTable());
        List<String> columns = step.columns().stream().filter(available::contains).toList();
        if (columns.isEmpty()) {
            log.warn("skip {}: no shared columns", step.sourceTable());
            return new long[]{0, 0};
        }
        List<String> missing = step.columns().stream().filter(column -> !available.contains(column)).toList();

        long rows = 0;
        long copied = 0;
        // 列名统一加反引号：旧库存在 desc 这类保留字列。
        String quotedColumns = columns.stream()
                .map(column -> "`" + column + "`")
                .collect(java.util.stream.Collectors.joining(", "));
        String select = "SELECT " + quotedColumns + " FROM " + step.sourceTable();
        String placeholders = String.join(", ", java.util.Collections.nCopies(columns.size(), "?"));
        String insert = "INSERT IGNORE INTO " + step.targetTable()
                + " (" + quotedColumns + ") VALUES (" + placeholders + ")";

        if (dryRun) {
            try (PreparedStatement read = source.prepareStatement(select);
                 ResultSet resultSet = read.executeQuery()) {
                while (resultSet.next()) {
                    rows++;
                }
            }
        } else {
            try (Connection targetConnection = target.getDataSource().getConnection();
                 PreparedStatement read = source.prepareStatement(select);
                 ResultSet resultSet = read.executeQuery();
                 PreparedStatement write = targetConnection.prepareStatement(insert)) {
                int batch = 0;
                while (resultSet.next()) {
                    rows++;
                    for (int i = 1; i <= columns.size(); i++) {
                        write.setObject(i, resultSet.getObject(i));
                    }
                    write.addBatch();
                    batch++;
                    if (batch >= BATCH_SIZE) {
                        copied += executeBatch(write);
                        batch = 0;
                    }
                }
                if (batch > 0) {
                    copied += executeBatch(write);
                }
            }
        }
        if (!missing.isEmpty()) {
            log.info("{}: source missing columns {} → filled with defaults/null", step.targetTable(), missing);
        }
        return new long[]{rows, copied};
    }

    private int executeBatch(PreparedStatement statement) throws SQLException {
        int[] results = statement.executeBatch();
        int copied = 0;
        for (int result : results) {
            copied += result >= 0 ? result : 1;
        }
        return copied;
    }

    private boolean tableExists(Connection source, String table) throws SQLException {
        try (var statement = source.prepareStatement(
                "SELECT 1 FROM information_schema.tables WHERE table_schema = database() AND table_name = ?")) {
            statement.setString(1, table);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private Set<String> columnsOf(Connection source, String table) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (Statement statement = source.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT * FROM " + table + " WHERE 1 = 0")) {
            ResultSetMetaData meta = resultSet.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                columns.add(meta.getColumnLabel(i).toLowerCase(Locale.ROOT));
            }
        }
        return columns;
    }

    private void finish(int code) {
        if (exit) {
            System.exit(code);
        }
    }
}
