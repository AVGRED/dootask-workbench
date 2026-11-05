import java.sql.Connection;
import java.sql.DriverManager;

import org.flywaydb.core.Flyway;

/** 一次性工具：修正失败的 V30 迁移记录（repair），让应用可以正常启动。 */
public class FlywayRepair {
    public static void main(String[] args) throws Exception {
        String url = System.getenv("DB_URL");
        String user = System.getenv("DB_USER");
        String pass = System.getenv("DB_PASS");
        try (Connection ignored = DriverManager.getConnection(url, user, pass)) {
            // 仅用于确认账号可连
        }
        Flyway flyway = Flyway.configure()
                .dataSource(url, user, pass)
                .locations("classpath:db/migration")
                .load();
        flyway.repair();
        System.out.println("repair done");
    }
}
