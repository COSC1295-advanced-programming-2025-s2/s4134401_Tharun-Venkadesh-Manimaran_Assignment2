package rmit.s4134401.carehome;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Prescription implements Serializable {

    public static class Line implements Serializable {
        public final String medicine;
        public final String dose;     
        public final String times;    
        public Line(String medicine, String dose, String times){
            this.medicine = medicine;
            this.dose = dose;
            this.times = times;
        }
        @Override public String toString(){
            return medicine + " " + dose + " @ " + times;
        }
    }

    private final String patientId;
    private final List<Line> lines = new ArrayList<Line>();

    public Prescription(String patientId){
        this.patientId = patientId;
    }

    public String patientId(){ return patientId; }

    public void addLine(String medicine, String dose, String times){
        lines.add(new Line(medicine, dose, times));
    }

    public List<Line> getLines(){ return lines; }

    @Override public String toString(){
        String s = "[" + patientId + "]: ";
        for (int i=0;i<lines.size();i++){
            s += lines.get(i).toString();
            if (i<lines.size()-1) s += "; ";
        }
        return s;
    }
}
