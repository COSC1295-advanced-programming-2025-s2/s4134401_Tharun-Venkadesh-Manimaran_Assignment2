package rmit.s4134401.carehome;

import java.io.Serializable;
import java.time.DayOfWeek;
import java.time.LocalTime;

public class MedicationAdministration implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String patientId;
    private final String medicine;
    private final String dose;
    private final DayOfWeek day;
    private final LocalTime time;
    private final String staffId;

    public MedicationAdministration(String patientId,
                                    String medicine,
                                    String dose,
                                    DayOfWeek day,
                                    LocalTime time,
                                    String staffId) {
        if (patientId == null || medicine == null || dose == null || day == null || time == null || staffId == null)
            throw new IllegalArgumentException("null admin field");
        this.patientId = patientId;
        this.medicine = medicine;
        this.dose = dose;
        this.day = day;
        this.time = time;
        this.staffId = staffId;
    }

    public String patientId() { return patientId; }
    public String medicine()  { return medicine; }
    public String dose()      { return dose; }
    public DayOfWeek day()    { return day; }
    public LocalTime time()   { return time; }
    public String staffId()   { return staffId; }

    @Override
    public String toString() {
        return "[" + patientId + "] " + medicine + " " + dose +
               " on " + day + " " + time + " by " + staffId;
    }
}
