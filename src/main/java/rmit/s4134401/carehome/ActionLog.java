package rmit.s4134401.carehome;

import java.io.Serializable;
import java.time.Instant;

public class ActionLog implements Serializable {
    private static final long serialVersionUID = 1L;

    public final Instant when;
    public final String staffId;
    public final ActionType type;
    public final String details;

    public ActionLog(Instant when, String staffId, ActionType type, String details){
        this.when = when; this.staffId = staffId; this.type = type; this.details = details;
    }

    @Override public String toString(){ return when + " | " + staffId + " | " + type + " | " + details; }
}
