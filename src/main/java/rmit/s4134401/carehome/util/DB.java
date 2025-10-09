package rmit.s4134401.carehome.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public final class DB {
    private static HikariDataSource ds;

    private DB(){}

    public static synchronized void init(String sqliteFilePath){
        if (ds != null) return;
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:sqlite:" + sqliteFilePath);
        cfg.setMaximumPoolSize(4);
        cfg.setAutoCommit(true);
        ds = new HikariDataSource(cfg);
    }

    public static Connection get() throws SQLException {
        if (ds == null) throw new IllegalStateException("DB not initialised. Call DB.init().");
        return ds.getConnection();
    }

    public static synchronized void shutdown(){
        if (ds != null) { ds.close(); ds = null; }
    }
}
