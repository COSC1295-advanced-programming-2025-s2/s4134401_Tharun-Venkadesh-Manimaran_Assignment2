package rmit.s4134401.carehome.repo.jdbc;

import rmit.s4134401.carehome.Prescription;
import rmit.s4134401.carehome.repo.PrescriptionRepository;
import rmit.s4134401.carehome.util.DB;

import java.sql.*;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.*;

public class JdbcPrescriptionRepository implements PrescriptionRepository {

    public int createPrescription(String patientId, String doctorId, DayOfWeek day, Instant createdTs) {
        try (Connection c = DB.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO prescriptions(patient_id,doctor_id,day,created_ts) VALUES(?,?,?,?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, patientId);
            ps.setString(2, doctorId);
            ps.setString(3, day.name());
            ps.setString(4, createdTs.toString());
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
            throw new RuntimeException("no rx id generated");
        } catch (SQLException e) {
            throw new RuntimeException("createPrescription failed: " + e.getMessage(), e);
        }
    }

    public void addLine(int rxId, String medicine, String dose, String times) {
        try (Connection c = DB.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO rx_lines(rx_id,medicine,dose,times) VALUES(?,?,?,?)")) {
            ps.setInt(1, rxId);
            ps.setString(2, medicine);
            ps.setString(3, dose);
            ps.setString(4, times);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("addLine failed: " + e.getMessage(), e);
        }
    }

    public List<Prescription> loadForPatient(String patientId) {
        Map<Integer, Prescription> map = new LinkedHashMap<Integer, Prescription>();
        try (Connection c = DB.get()) {
            PreparedStatement ps = c.prepareStatement(
                    "SELECT id FROM prescriptions WHERE patient_id=? ORDER BY id");
            ps.setString(1, patientId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int rxId = rs.getInt(1);
                map.put(rxId, new Prescription(patientId));
            }
            rs.close(); ps.close();

            if (map.isEmpty()) return new ArrayList<Prescription>();

            PreparedStatement ps2 = c.prepareStatement(
                    "SELECT rx_id,medicine,dose,times FROM rx_lines WHERE rx_id IN (" +
                            joinQMarks(map.keySet().size()) + ") ORDER BY id");
            int i = 1;
            for (Integer id : map.keySet()) ps2.setInt(i++, id.intValue());
            ResultSet rs2 = ps2.executeQuery();
            while (rs2.next()) {
                int rxId = rs2.getInt(1);
                String med = rs2.getString(2);
                String dose = rs2.getString(3);
                String times = rs2.getString(4);
                map.get(rxId).addLine(med, dose, times);
            }
            rs2.close(); ps2.close();
            return new ArrayList<Prescription>(map.values());
        } catch (SQLException e) {
            throw new RuntimeException("loadForPatient failed: " + e.getMessage(), e);
        }
    }

    private static String joinQMarks(int n){
        StringBuilder sb = new StringBuilder();
        for (int i=0;i<n;i++){
            if (i>0) sb.append(',');
            sb.append('?');
        }
        return sb.toString();
    }
}
