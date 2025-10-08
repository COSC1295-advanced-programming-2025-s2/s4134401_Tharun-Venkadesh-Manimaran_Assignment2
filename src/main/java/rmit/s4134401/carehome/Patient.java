package rmit.s4134401.carehome;

import java.io.Serializable;

public class Patient implements Serializable {
    private final String patientId;
    private final String fullName;
    private final Gender gender;
    private final boolean isolation;

    public Patient(String patientId, String fullName, Gender gender, boolean isolation){
        if (patientId == null || fullName == null || gender == null) throw new IllegalArgumentException("null patient");
        this.patientId = patientId; this.fullName = fullName; this.gender = gender; this.isolation = isolation;
    }

    public String id(){ return patientId; }
    public Gender gender(){ return gender; }
    public boolean needsIsolation(){ return isolation; }

    @Override public String toString(){
        return fullName + " (" + patientId + ", " + gender + (isolation?", isolation":"") + ")";
    }
}
