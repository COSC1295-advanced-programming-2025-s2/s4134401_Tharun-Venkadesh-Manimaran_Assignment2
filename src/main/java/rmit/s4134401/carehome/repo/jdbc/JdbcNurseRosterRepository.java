package rmit.s4134401.carehome.repo.jdbc;

import rmit.s4134401.carehome.repo.NurseRosterRepository;
import rmit.s4134401.carehome.util.DB;

import java.sql.*;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class JdbcNurseRosterRepository implements NurseRosterRepository {

    public void addShift(String nurseId, DayOfWeek day, LocalTime start, LocalTime end) {
        try (Connection c = DB.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT OR IGNORE INTO nurse_shifts(nurse_id,day,start,end) VALUES(?,?,?,?)")) {
            ps.setString(1, nurseId);
            ps.setString(2, day.name());
            ps.setString(3, start.toString());
            ps.setString(4, end.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("addShift failed: " + e.getMessage(), e);
        }
    }

    public int removeShift(String nurseId, DayOfWeek day, LocalTime start, LocalTime end) {
        try (Connection c = DB.get();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM nurse_shifts WHERE nurse_id=? AND day=? AND start=? AND end=?")) {
            ps.setString(1, nurseId);
            ps.setString(2, day.name());
            ps.setString(3, start.toString());
            ps.setString(4, end.toString());
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("removeShift failed: " + e.getMessage(), e);
        }
    }

    public List<int[]> listShiftsFor(String nurseId) {
        List<int[]> out = new ArrayList<int[]>();
        try (Connection c = DB.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT day,start,end FROM nurse_shifts WHERE nurse_id=?")) {
            ps.setString(1, nurseId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                DayOfWeek d = DayOfWeek.valueOf(rs.getString(1));
                LocalTime s = LocalTime.parse(rs.getString(2));
                LocalTime e = LocalTime.parse(rs.getString(3));
                out.add(new int[]{ d.ordinal(), s.getHour(), e.getHour() });
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("listShiftsFor failed: " + e.getMessage(), e);
        }
    }

    public long totalHoursFor(String nurseId, DayOfWeek day) {
        long sum = 0L;
        try (Connection c = DB.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT start,end FROM nurse_shifts WHERE nurse_id=? AND day=?")) {
            ps.setString(1, nurseId);
            ps.setString(2, day.name());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                LocalTime s = LocalTime.parse(rs.getString(1));
                LocalTime e = LocalTime.parse(rs.getString(2));
                sum += Duration.between(s, e).toHours();
            }
            return sum;
        } catch (SQLException e) {
            throw new RuntimeException("totalHoursFor failed: " + e.getMessage(), e);
        }
    }

    public boolean hasShift(String nurseId, DayOfWeek day, LocalTime t) {
        try (Connection c = DB.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT 1 FROM nurse_shifts WHERE nurse_id=? AND day=? AND start<=? AND end>?")) {
            ps.setString(1, nurseId);
            ps.setString(2, day.name());
            ps.setString(3, t.toString());
            ps.setString(4, t.toString());
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            throw new RuntimeException("hasShift failed: " + e.getMessage(), e);
        }
    }

    public boolean dayCovered(DayOfWeek day, LocalTime start, LocalTime end) {
        try (Connection c = DB.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT 1 FROM nurse_shifts WHERE day=? AND start=? AND end=? LIMIT 1")) {
            ps.setString(1, day.name());
            ps.setString(2, start.toString());
            ps.setString(3, end.toString());
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            throw new RuntimeException("dayCovered failed: " + e.getMessage(), e);
        }
    }
}
