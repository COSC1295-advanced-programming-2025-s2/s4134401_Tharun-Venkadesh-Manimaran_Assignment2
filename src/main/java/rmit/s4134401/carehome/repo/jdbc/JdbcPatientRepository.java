package rmit.s4134401.carehome.repo.jdbc;

import rmit.s4134401.carehome.Gender;
import rmit.s4134401.carehome.repo.PatientRepository;
import rmit.s4134401.carehome.util.DB;

import java.sql.*;
import java.util.Optional;

public class JdbcPatientRepository implements PatientRepository {

    public void add(String id, String fullName, Gender gender, boolean isolation) {
        try (Connection c = DB.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO patients(id,full_name,gender,isolation,bed_id) VALUES(?,?,?,?,NULL)")) {
            ps.setString(1, id);
            ps.setString(2, fullName);
            ps.setString(3, gender.name());
            ps.setInt(4, isolation ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("add patient failed: " + e.getMessage(), e);
        }
    }

    public Optional<String> findOccupantByBedId(int bedId) {
        try (Connection c = DB.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id FROM patients WHERE bed_id=?")) {
            ps.setInt(1, bedId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return Optional.empty();
            return Optional.of(rs.getString(1));
        } catch (SQLException e) {
            throw new RuntimeException("findOccupantByBedId failed: " + e.getMessage(), e);
        }
    }

    public void assignToBed(String patientId, int bedId) {
        try (Connection c = DB.get();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE patients SET bed_id=? WHERE id=?")) {
            ps.setInt(1, bedId);
            ps.setString(2, patientId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("assignToBed failed: " + e.getMessage(), e);
        }
    }

    public void vacateBed(int bedId) {
        try (Connection c = DB.get();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE patients SET bed_id=NULL WHERE bed_id=?")) {
            ps.setInt(1, bedId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("vacateBed failed: " + e.getMessage(), e);
        }
    }

    public boolean existsPatientId(String patientId) {
        try (Connection c = DB.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT 1 FROM patients WHERE id=?")) {
            ps.setString(1, patientId);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            throw new RuntimeException("existsPatientId failed: " + e.getMessage(), e);
        }
    }
}
