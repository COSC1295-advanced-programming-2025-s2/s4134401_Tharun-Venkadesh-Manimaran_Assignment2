package rmit.s4134401.carehome.repo;

import rmit.s4134401.carehome.Gender;

import java.util.Optional;

public interface PatientRepository {
    void add(String id, String fullName, Gender gender, boolean isolation);
    Optional<String> findOccupantByBedId(int bedId);     
    void assignToBed(String patientId, int bedId);       
    void vacateBed(int bedId);                           
    boolean existsPatientId(String patientId);
}
