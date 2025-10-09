package rmit.s4134401.carehome.repo;

import rmit.s4134401.carehome.Role;
import rmit.s4134401.carehome.Staff;

import java.util.Map;
import java.util.Optional;

public interface StaffRepository {
    void add(Staff s);
    Optional<Staff> find(String id);
    void rename(String id, String newName);
    void setPassword(String id, String pw);
    Map<Role, Long> countsByRole();
}
