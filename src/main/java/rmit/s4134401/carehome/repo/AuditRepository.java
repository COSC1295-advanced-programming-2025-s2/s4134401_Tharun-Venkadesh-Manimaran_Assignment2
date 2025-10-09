package rmit.s4134401.carehome.repo;

import rmit.s4134401.carehome.ActionType;

import java.time.Instant;

public interface AuditRepository {
    void log(Instant when, String staffId, ActionType type, String details);
}
