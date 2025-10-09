package rmit.s4134401.carehome.repo.jdbc;

import rmit.s4134401.carehome.MedicationAdministration;
import rmit.s4134401.carehome.repo.AdministrationRepository;
import rmit.s4134401.carehome.util.DB;

import java.sql.*;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class JdbcAdministrationRepository implements AdministrationRepository {

    public void add(String patientId, String medicine, String dose, DayOfWeek day, LocalTime time,
                    String staffId, boolean correction) {
        try (Connection c = DB.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO administrations(patient_id,medicine,dose,day,time,staff_id,is_correction) " +
                             "VALUES(?,?,?,?,?,?,?)")) {
            ps.setString(1, patientId);
            ps.setString(2, medicine);
            ps.setString(3, dose);
            ps.setString(4, day.name());
            ps.setString(5, time.toString());
            ps.setString(6, staffId);
            ps.setInt(7, correction ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("add administration failed: " + e.getMessage(), e);
        }
    }

    public List<MedicationAdministration> listForPatient(String patientId) {
        List<MedicationAdministration> out = new ArrayList<MedicationAdministration>();
        try (Connection c = DB.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT medicine,dose,day,time,staff_id FROM administrations WHERE patient_id=? ORDER BY id")) {
            ps.setString(1, patientId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String med = rs.getString(1);
                String dose = rs.getString(2);
                DayOfWeek day = DayOfWeek.valueOf(rs.getString(3));
                LocalTime t = LocalTime.parse(rs.getString(4));
                String staff = rs.getString(5);
                out.add(new MedicationAdministration(patientId, med, dose, day, t, staff));
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("listForPatient failed: " + e.getMessage(), e);
        }
    }
}
