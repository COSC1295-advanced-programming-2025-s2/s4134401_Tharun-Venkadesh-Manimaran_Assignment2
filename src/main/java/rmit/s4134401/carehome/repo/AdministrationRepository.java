package rmit.s4134401.carehome.repo;

import rmit.s4134401.carehome.MedicationAdministration;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public interface AdministrationRepository {
    void add(String patientId, String medicine, String dose, DayOfWeek day, LocalTime time, String staffId, boolean correction);
    List<MedicationAdministration> listForPatient(String patientId);
}
