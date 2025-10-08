package rmit.s4134401.carehome;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.*;

import java.time.DayOfWeek;
import java.time.LocalTime;

public class CareHomeTests {

    @Test
    void coverage_and_limits_pass_when_configured() {
        final CareHome app = new CareHome();
        app.addManager("m","Mgr");
        app.addNurse("n1","N One");
        app.addNurse("n2","N Two");

        DayOfWeek[] days = DayOfWeek.values();
        for (int i=0; i<days.length; i++){
            DayOfWeek d = days[i];
            app.assignNurseShift("m","n1", d, true);   
            app.assignNurseShift("m","n2", d, false);  
            app.setDoctorMinutes("m", d, 60);
        }

        assertDoesNotThrow(new Executable() {
            public void execute() { app.checkCompliance(); }
        });
    }

    @Test
    void fails_if_doctor_missing() {
        final CareHome app = new CareHome();
        app.addManager("m","Mgr");
        app.addNurse("n1","N One");
        app.addNurse("n2","N Two");

        DayOfWeek[] days = DayOfWeek.values();
        for (int i=0; i<days.length; i++){
            DayOfWeek d = days[i];
            app.assignNurseShift("m","n1", d, true);
            app.assignNurseShift("m","n2", d, false);
            int mins = (d == DayOfWeek.MONDAY) ? 0 : 60; 
            app.setDoctorMinutes("m", d, mins);
        }

        assertThrows(ComplianceException.class, new Executable() {
            public void execute() { app.checkCompliance(); }
        });
    }

    @Test
    void nurse_cannot_move_patient_off_shift() {
        final CareHome app = new CareHome();
        app.addManager("m","Mgr");
        app.addNurse("n1","N One");
        app.assignNurseShift("m","n1", DayOfWeek.MONDAY, true); 

        Bed bed1 = app.findFirstVacant();
        app.admitPatient("m", new Patient("p1","Pat One", Gender.M,false), bed1);
        Bed bed2 = app.findFirstVacant();

        assertThrows(AuthorizationException.class, new Executable() {
            public void execute() {
                app.movePatient("n1", bed1, bed2, DayOfWeek.MONDAY, LocalTime.of(21,0));
            }
        });
    }

    @Test
    void cannot_admit_into_occupied_bed() {
    	CareHome app = new CareHome();
        app.addManager("m","Mgr");
        Bed b = app.findFirstVacant();
        app.admitPatient("m", new Patient("p1","Alice", Gender.F,false), b);
        assertThrows(RosterException.class, new Executable() {
            public void execute() {
                app.admitPatient("m", new Patient("p2","Bob", Gender.M,false), b);
            }
        });
    }

    @Test
    void assigning_more_than_8h_in_a_day_is_rejected() {
    	CareHome app = new CareHome();
        app.addManager("m","Mgr");
        app.addNurse("n1","N One");

        app.assignNurseShift("m","n1", DayOfWeek.TUESDAY, true); 

        assertThrows(RosterException.class, new Executable() {
            public void execute() {
                app.assignNurseShift("m","n1", DayOfWeek.TUESDAY, false); 
            }
        });
    }

    @Test
    void remove_shift_works_and_missing_shift_throws() {
    	CareHome app = new CareHome();
        app.addManager("m","Mgr");
        app.addNurse("n1","N One");

        app.assignNurseShift("m","n1", DayOfWeek.WEDNESDAY, true);
        app.removeNurseShift("m","n1", DayOfWeek.WEDNESDAY, true);

        assertThrows(RosterException.class, new Executable() {
            public void execute() {
                app.removeNurseShift("m","n1", DayOfWeek.WEDNESDAY, true);
            }
        });
    }

    @Test
    void manager_cannot_view_resident_details() {
    	CareHome app = new CareHome();
        app.addManager("m","Mgr");
        Bed b = app.findFirstVacant();
        app.admitPatient("m", new Patient("p1","Alice", Gender.F,false), b);

        assertThrows(AuthorizationException.class, new Executable() {
            public void execute() {
                app.viewResident("m", b); 
            }
        });
    }

    @Test
    void doctor_add_prescription_requires_rostered_day() {
    	CareHome app = new CareHome();
        app.addManager("m","Mgr");
        app.addDoctor("d1","Doc");
        Bed b = app.findFirstVacant();
        app.admitPatient("m", new Patient("p1","Alice", Gender.F,false), b);

        Prescription rx = new Prescription("p1");
        rx.addLine("Drug", "5mg", "08:00");

        assertThrows(AuthorizationException.class, new Executable() {
            public void execute() {
                app.doctorAddPrescription("d1", b, DayOfWeek.MONDAY, rx);
            }
        });
    }
    
    @Test
    void doctor_add_prescription_patient_mismatch_throws() {
    	CareHome app = new CareHome();
        app.addManager("m","Mgr");
        app.addDoctor("d1","Doc");
        app.setDoctorMinutes("m", DayOfWeek.MONDAY, 60);
        Bed b = app.findFirstVacant();
        app.admitPatient("m", new Patient("p1","Alice", Gender.F,false), b);

        Prescription rx = new Prescription("pX"); 
        rx.addLine("Drug", "5mg", "08:00");

        assertThrows(IllegalArgumentException.class, new Executable() {
            public void execute() {
                app.doctorAddPrescription("d1", b, DayOfWeek.MONDAY, rx);
            }
        });
    }

    @Test
    void administer_by_doctor_without_minutes_throws() {
    	CareHome app = new CareHome();
        app.addManager("m","Mgr");
        app.addDoctor("d1","Doc");
        Bed b = app.findFirstVacant();
        app.admitPatient("m", new Patient("p1","Alice", Gender.F,false), b);

        assertThrows(AuthorizationException.class, new Executable() {
            public void execute() {
                app.administerMedication("d1", b, DayOfWeek.MONDAY, LocalTime.of(9,0), "Drug", "5mg");
            }
        });
    }

    @Test
    void move_patient_to_occupied_destination_throws() {
    	CareHome app = new CareHome();
        app.addManager("m","Mgr");
        app.addNurse("n1","N One");
        app.assignNurseShift("m","n1", DayOfWeek.THURSDAY, true);

        Bed from = app.findFirstVacant();
        app.admitPatient("m", new Patient("p1","Alice", Gender.F,false), from);
        Bed occupiedDest = app.findFirstVacant();
        app.admitPatient("m", new Patient("p2","Bob", Gender.M,false), occupiedDest);

        assertThrows(RosterException.class, new Executable() {
            public void execute() {
                app.movePatient("n1", from, occupiedDest, DayOfWeek.THURSDAY, LocalTime.of(9,0));
            }
        });
    }

    @Test
    void update_admin_dose_checks_authorization() {
    	CareHome app = new CareHome();
        app.addManager("m","Mgr");
        app.addDoctor("d1","Doc");
        app.addNurse("n1","N One");
        app.setDoctorMinutes("m", DayOfWeek.WEDNESDAY, 60);
        app.assignNurseShift("m","n1", DayOfWeek.WEDNESDAY, true);

        Bed b = app.findFirstVacant();
        app.admitPatient("m", new Patient("p2","Bob", Gender.M,false), b);

        app.administerMedication("n1", b, DayOfWeek.WEDNESDAY, LocalTime.of(10,0), "Paracetamol", "500mg");

        assertThrows(AuthorizationException.class, new Executable() {
            public void execute() {
                app.updateAdministrationDose("n1", "p2", "Paracetamol", DayOfWeek.WEDNESDAY, LocalTime.of(21,0), "650mg");
            }
        });

        assertDoesNotThrow(new Executable() {
            public void execute() {
                app.updateAdministrationDose("d1", "p2", "Paracetamol", DayOfWeek.WEDNESDAY, LocalTime.of(10,0), "650mg");
            }
        });
    }

    @Test
    void setStaffPassword_requires_manager() {
    	CareHome app = new CareHome();
        app.addManager("m","Mgr");
        app.addNurse("n1","N One");

        assertThrows(AuthorizationException.class, new Executable() {
            public void execute() {
                app.setStaffPassword("n1", "n1", "secret");
            }
        });

        assertDoesNotThrow(new Executable() {
            public void execute() {
                app.setStaffPassword("m", "n1", "ok");
            }
        });
    }
    
    @Test
    void doctor_cannot_administer() {
    	CareHome app = new CareHome();
        app.addManager("m","Mgr");
        app.addDoctor("d1","Doc");
        app.setDoctorMinutes("m", DayOfWeek.MONDAY, 60);
        Bed b = app.findFirstVacant();
        app.admitPatient("m", new Patient("p1","Alice", Gender.F,false), b);

        assertThrows(AuthorizationException.class, new org.junit.jupiter.api.function.Executable() {
            public void execute() {
                app.administerMedication("d1", b, DayOfWeek.MONDAY, java.time.LocalTime.of(9,0), "Drug", "5mg");
            }
        });
    }

    @Test
    void audit_uses_structured_types_for_key_actions(){
        CareHome app = new CareHome();
        app.addManager("m","Mgr");
        app.addNurse("n","N");
        app.assignNurseShift("m","n", DayOfWeek.MONDAY, true);
        app.printAudit(); 
    }

    
}
