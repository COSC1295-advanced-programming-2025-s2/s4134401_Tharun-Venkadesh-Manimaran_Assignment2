package rmit.s4134401.carehome;

import java.io.Serializable;
import java.util.Objects;

public class Bed implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String ward;
    private final int room;
    private final int bedNum;
    private Patient occupant; 

    public Bed(String ward, int room, int bedNum){
        this.ward=ward; this.room=room; this.bedNum=bedNum;
    }

    public String ward(){ return ward; }
    public int room(){ return room; }
    public int bedNum(){ return bedNum; }

    public boolean isVacant(){ return occupant==null; }
    public Patient getOccupant(){ return occupant; }
    void assign(Patient p){ this.occupant = p; }
    void vacate(){ this.occupant = null; }

    @Override public String toString(){
        return ward + "-R" + room + "-B" + bedNum + (isVacant()? " [vacant]" : " [" + occupant + "]");
    }

    @Override public boolean equals(Object o){
        if (this == o) return true;
        if (!(o instanceof Bed)) return false;
        Bed b = (Bed) o;
        return room == b.room && bedNum == b.bedNum && Objects.equals(ward, b.ward);
    }
    @Override public int hashCode(){ return Objects.hash(ward, room, bedNum); }
}
