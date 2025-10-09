package rmit.s4134401.carehome.repo;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public interface NurseRosterRepository {
    void addShift(String nurseId, DayOfWeek day, LocalTime start, LocalTime end);
    int removeShift(String nurseId, DayOfWeek day, LocalTime start, LocalTime end);
    List<int[]> listShiftsFor(String nurseId); 
    long totalHoursFor(String nurseId, DayOfWeek day);
    boolean hasShift(String nurseId, DayOfWeek day, LocalTime t);
    boolean dayCovered(DayOfWeek day, LocalTime start, LocalTime end);
}
