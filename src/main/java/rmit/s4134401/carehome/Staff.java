package rmit.s4134401.carehome;

import java.io.Serializable;

public abstract class Staff implements Serializable {
    private final String staffId;
    private String name;
    private final Role role;
    private String password; 

    protected Staff(String staffId, String name, Role role) {
        if (staffId == null || name == null || role == null) throw new IllegalArgumentException("null arg");
        this.staffId = staffId;
        this.name = name;
        this.role = role;
        this.password = ""; 
    }

    public String id() { return staffId; }
    public String name() { return name; }
    public Role role() { return role; }

    public void rename(String newName) {
        if (newName == null) throw new IllegalArgumentException("null");
        this.name = newName;
    }

    public void setPassword(String newPassword){
        if (newPassword == null) throw new IllegalArgumentException("null");
        this.password = newPassword;
    }

    public String getPassword(){ return password; } 

    @Override public String toString() {
        return role + "{" + staffId + ": " + name + "}";
    }
}
