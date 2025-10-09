package rmit.s4134401.carehome.repo;

import rmit.s4134401.carehome.Prescription;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.List;

public interface PrescriptionRepository {
    int createPrescription(String patientId, String doctorId, DayOfWeek day, Instant createdTs);
    void addLine(int rxId, String medicine, String dose, String times);
    List<Prescription> loadForPatient(String patientId);
}
