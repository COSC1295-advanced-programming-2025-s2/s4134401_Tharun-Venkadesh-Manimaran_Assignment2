package rmit.s4134401.carehome;

import java.io.Serializable;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;

public class Shift implements Serializable {
    public final DayOfWeek day;
    public final LocalTime start;
    public final LocalTime end;

    public Shift(DayOfWeek day, LocalTime start, LocalTime end) {
        if (day == null || start == null || end == null) throw new IllegalArgumentException("null shift");
        if (!end.isAfter(start)) throw new IllegalArgumentException("shift end must be after start");
        this.day = day; this.start = start; this.end = end;
    }

    public long hours(){ return Duration.between(start, end).toHours(); }

    @Override public boolean equals(Object o){
        if(!(o instanceof Shift)) return false;
        Shift s = (Shift)o;
        return day==s.day && start.equals(s.start) && end.equals(s.end);
    }

    @Override public int hashCode(){ return day.hashCode() * 31 + start.hashCode() * 17 + end.hashCode(); }

    @Override public String toString(){ return day + " " + start + "-" + end; }
}
