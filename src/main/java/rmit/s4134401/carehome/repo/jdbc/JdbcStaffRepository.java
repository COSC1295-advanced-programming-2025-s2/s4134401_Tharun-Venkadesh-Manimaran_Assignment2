package rmit.s4134401.carehome.repo.jdbc;

import rmit.s4134401.carehome.*;
import rmit.s4134401.carehome.repo.StaffRepository;
import rmit.s4134401.carehome.util.DB;

import java.sql.*;
import java.util.*;

public class JdbcStaffRepository implements StaffRepository {

    public void add(Staff s) {
        try (Connection c = DB.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO staff(id,name,role,password) VALUES(?,?,?,?)")) {
            ps.setString(1, s.id());
            ps.setString(2, s.name());
            ps.setString(3, s.role().name());
            ps.setString(4, s.getPassword() == null ? "" : s.getPassword());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("add staff failed: " + e.getMessage(), e);
        }
    }

    public Optional<Staff> find(String id) {
        try (Connection c = DB.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id,name,role,password FROM staff WHERE id=?")) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return Optional.empty();

            String name = rs.getString("name");
            Role role = Role.valueOf(rs.getString("role"));
            Staff s;
            if (role == Role.MANAGER) s = new Manager(id, name);
            else if (role == Role.DOCTOR) s = new Doctor(id, name);
            else s = new Nurse(id, name);
            s.setPassword(rs.getString("password"));
            return Optional.of(s);
        } catch (SQLException e) {
            throw new RuntimeException("find staff failed: " + e.getMessage(), e);
        }
    }

    public void rename(String id, String newName) {
        try (Connection c = DB.get();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE staff SET name=? WHERE id=?")) {
            ps.setString(1, newName);
            ps.setString(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("rename failed: " + e.getMessage(), e);
        }
    }

    public void setPassword(String id, String pw) {
        try (Connection c = DB.get();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE staff SET password=? WHERE id=?")) {
            ps.setString(1, pw);
            ps.setString(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("setPassword failed: " + e.getMessage(), e);
        }
    }

    public Map<Role, Long> countsByRole() {
        Map<Role, Long> out = new EnumMap<Role, Long>(Role.class);
        try (Connection c = DB.get();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT role, COUNT(*) cnt FROM staff GROUP BY role")) {
            while (rs.next()) {
                out.put(Role.valueOf(rs.getString("role")), rs.getLong("cnt"));
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("countsByRole failed: " + e.getMessage(), e);
        }
    }
}
