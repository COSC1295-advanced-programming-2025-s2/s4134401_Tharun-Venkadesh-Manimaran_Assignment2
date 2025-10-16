package rmit.s4134401.carehome.repo;

import java.time.DayOfWeek;
import java.util.Map;

public interface DoctorMinutesRepository {
	void upsertDoctorMinutes(String doctorId, java.time.DayOfWeek day, int minutes);
    void setMinutes(DayOfWeek day, int minutes);
    int getMinutes(DayOfWeek day);
    Map<DayOfWeek,Integer> all();
}
