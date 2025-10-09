package rmit.s4134401.carehome.repo.jdbc;

import rmit.s4134401.carehome.ActionType;
import rmit.s4134401.carehome.repo.AuditRepository;
import rmit.s4134401.carehome.util.DB;

import java.sql.*;
import java.time.Instant;

public class JdbcAuditRepository implements AuditRepository {
    public void log(Instant when, String staffId, ActionType type, String details) {
        try (Connection c = DB.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO audit(when_ts,staff_id,type,details) VALUES(?,?,?,?)")) {
            ps.setString(1, when.toString());
            ps.setString(2, staffId);
            ps.setString(3, type.name());
            ps.setString(4, details);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("audit log failed: " + e.getMessage(), e);
        }
    }
}
