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

    public int getMinutes(DayOfWeek day) {
        try (Connection c = DB.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT minutes FROM doctor_minutes WHERE day=?")) {
            ps.setString(1, day.name());
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return 0;
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new RuntimeException("getMinutes failed: " + e.getMessage(), e);
        }
    }

    public Map<DayOfWeek, Integer> all() {
        Map<DayOfWeek, Integer> m = new EnumMap<DayOfWeek, Integer>(DayOfWeek.class);
        try (Connection c = DB.get(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT day,minutes FROM doctor_minutes")) {
            while (rs.next()) {
                m.put(DayOfWeek.valueOf(rs.getString(1)), rs.getInt(2));
            }
            return m;
        } catch (SQLException e) {
            throw new RuntimeException("all minutes failed: " + e.getMessage(), e);
        }
    }
}
