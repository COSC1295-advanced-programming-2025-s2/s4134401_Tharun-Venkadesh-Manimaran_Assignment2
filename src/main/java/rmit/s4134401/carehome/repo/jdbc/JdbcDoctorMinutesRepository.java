package rmit.s4134401.carehome.repo.jdbc;

import rmit.s4134401.carehome.repo.DoctorMinutesRepository;
import rmit.s4134401.carehome.util.DB;

import java.sql.*;
import java.time.DayOfWeek;
import java.util.EnumMap;
import java.util.Map;

public class JdbcDoctorMinutesRepository implements DoctorMinutesRepository {

    public void setMinutes(DayOfWeek day, int minutes) {
        try (Connection c = DB.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO doctor_minutes(day,minutes) VALUES(?,?) " +
                     "ON CONFLICT(day) DO UPDATE SET minutes=excluded.minutes")) {
            ps.setString(1, day.name());
            ps.setInt(2, minutes);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("setMinutes failed: " + e.getMessage(), e);
        }
    }
    
    public void upsertDoctorMinutes(String doctorId, java.time.DayOfWeek day, int minutes) {
        String sql =
            "INSERT INTO doctor_minutes(doctor_id, day, minutes) " +
            "VALUES(?,?,?) " +
            "ON CONFLICT(doctor_id, day) DO UPDATE SET minutes=excluded.minutes";
        try (var c = DB.get(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, doctorId);                 
            ps.setString(2, day.name());
            ps.setInt(3, minutes);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public int getMinutes(DayOfWeek day) {
        try (var c = DB.get();
             var ps = c.prepareStatement("SELECT COALESCE(SUM(minutes),0) FROM doctor_minutes WHERE day=?")) {
            ps.setString(1, day.name());
            try (var rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        } catch (SQLException e) { throw new RuntimeException("getMinutes failed: " + e.getMessage(), e); }
    }


    @Override
    public Map<DayOfWeek, Integer> all() {
        Map<DayOfWeek, Integer> m = new EnumMap<>(DayOfWeek.class);
        String sql = "SELECT day, SUM(minutes) AS minutes FROM doctor_minutes GROUP BY day";
        try (Connection c = DB.get();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                m.put(DayOfWeek.valueOf(rs.getString("day")), rs.getInt("minutes"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("all minutes failed: " + e.getMessage(), e);
        }
        return m;
    }

}
