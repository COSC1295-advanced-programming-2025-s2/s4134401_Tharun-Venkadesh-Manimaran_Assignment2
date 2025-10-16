package rmit.s4134401.carehome.repo.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import rmit.s4134401.carehome.ActionType;
import rmit.s4134401.carehome.repo.AuditRepository;
import rmit.s4134401.carehome.util.DB;

public final class JdbcAuditRepository implements AuditRepository {

    public JdbcAuditRepository() {
        ensureTable();
    }

    private void ensureTable() {
        try (Connection c = DB.get(); Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS audit(
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  when_ts TEXT NOT NULL,
                  staff_id TEXT NOT NULL,
                  type TEXT NOT NULL,
                  details TEXT NOT NULL
                )
            """);
        } catch (Exception e) {
            throw new RuntimeException("ensureTable failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void log(java.time.Instant when, String staffId, ActionType type, String details) {
        try (Connection c = DB.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO audit(when_ts, staff_id, type, details) VALUES(?,?,?,?)")) {
            ps.setString(1, java.time.OffsetDateTime.now().toString());
            ps.setString(2, staffId);
            ps.setString(3, type.name());
            ps.setString(4, details);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("audit log failed: " + e.getMessage(), e);
        }
    }
}
