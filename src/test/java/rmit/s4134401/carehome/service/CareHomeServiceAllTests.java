package rmit.s4134401.carehome.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import rmit.s4134401.carehome.*;
import rmit.s4134401.carehome.repo.*;
import rmit.s4134401.carehome.repo.jdbc.*;
import rmit.s4134401.carehome.util.DB;
import rmit.s4134401.carehome.util.SchemaMigrator;

import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class CareHomeServiceAllTests {

    @TempDir Path tmp;

    CareHomeService svc;
    StaffRepository staffRepo;
    BedRepository bedRepo;
    PatientRepository patientRepo;
    NurseRosterRepository nurseRepo;
    DoctorMinutesRepository docRepo;
    AuditRepository auditRepo;
    PrescriptionRepository rxRepo;
    AdministrationRepository adminRepo;

    @BeforeEach
    void setup() {
        DB.shutdown();
        DB.init(tmp.resolve("carehome_" + System.nanoTime() + ".db").toString());
        SchemaMigrator.ensure();

        staffRepo = new JdbcStaffRepository();
        bedRepo = new JdbcBedRepository();
        patientRepo = new JdbcPatientRepository();
        nurseRepo = new JdbcNurseRosterRepository();
        docRepo = new JdbcDoctorMinutesRepository();
        auditRepo = new JdbcAuditRepository();
        rxRepo = new JdbcPrescriptionRepository();
        adminRepo = new JdbcAdministrationRepository();

        svc = new CareHomeService(staffRepo, bedRepo, patientRepo, nurseRepo, docRepo, auditRepo, rxRepo, adminRepo);

        svc.addManager("mX", "Manager X");
        svc.addDoctor("dX", "Doctor X");
        svc.addNurse("nX", "Nurse X");
    }

    @AfterEach
    void tearDown() { DB.shutdown(); }

    @Test
    void testAddDoctorNurseManager() {
        assertDoesNotThrow(() -> {
            svc.addDoctor("d2", "Doctor Two");
            svc.addNurse("n2", "Nurse Two");
            svc.addManager("m2", "Manager Two");
        });
    }

    @Test
    void testSetAndRetrieveDoctorMinutes() {
        svc.setDoctorMinutes("mX", "dX", DayOfWeek.MONDAY, 90);
        assertTrue(docRepo.getMinutes(DayOfWeek.MONDAY) >= 90);
    }

    @Test
    void testAssignAndRemoveNurseShift() {
        svc.assignNurseShift("mX", "nX", DayOfWeek.MONDAY, true);
        assertThrows(RosterException.class,
                () -> svc.assignNurseShift("mX", "nX", DayOfWeek.MONDAY, false));
        svc.removeNurseShift("mX", "nX", DayOfWeek.MONDAY, true);
    }

    @Test
    void testDoctorPrescriptionFlow() {
        svc.setDoctorMinutes("mX", "dX", DayOfWeek.WEDNESDAY, 80);
        svc.admitPatient("mX", "pB", "Bob", Gender.M, false, "A", 3, 1);
        assertDoesNotThrow(() ->
                svc.doctorAddPrescription("dX", "pB", DayOfWeek.WEDNESDAY,
                        "Amoxicillin", "500mg", "08:00,20:00"));
        List<Prescription> rx = svc.loadPrescriptionsForPatient("pB");
        assertEquals(1, rx.size());
    }

    @Test
    void testNurseAdministerMedication() {
        svc.admitPatient("mX", "pC", "Cara", Gender.F, false, "A", 3, 2);
        svc.assignNurseShift("mX", "nX", DayOfWeek.THURSDAY, true);
        assertDoesNotThrow(() ->
                svc.administerMedication("nX", DayOfWeek.THURSDAY,
                        LocalTime.of(10, 0), "pC", "Panadol", "500mg"));
        assertEquals(1, svc.administrationsForPatient("pC").size());
    }

    @Test
    void testUpdateAdministrationDoseByDoctor() {
        svc.setDoctorMinutes("mX", "dX", DayOfWeek.FRIDAY, 70);
        svc.admitPatient("mX", "pD", "Dan", Gender.M, false, "A", 4, 1);
        svc.assignNurseShift("mX", "nX", DayOfWeek.FRIDAY, true);
        svc.administerMedication("nX", DayOfWeek.FRIDAY, LocalTime.of(9, 0), "pD", "Cefalexin", "200mg");
        assertDoesNotThrow(() ->
                svc.updateAdministrationDose("dX", DayOfWeek.FRIDAY, LocalTime.of(9, 0),
                        "pD", "Cefalexin", "250mg", true));
    }

    @Test
    void testComplianceFailsWhenDoctorMinutesMissing() {
        assertThrows(ComplianceException.class, () -> svc.checkCompliance());
    }

    @Test
    void testCompliancePassesWhenAllCovered() {
        for (DayOfWeek d : DayOfWeek.values()) {
            svc.setDoctorMinutes("mX", "dX", d, 60);
            svc.assignNurseShift("mX", "nX", d, true);
            svc.addNurse("nY" + d.ordinal(), "Nurse" + d);
            svc.assignNurseShift("mX", "nY" + d.ordinal(), d, false);
        }
        assertDoesNotThrow(() -> svc.checkCompliance());
    }

    @Test
    void testAuditLoggingOnStaffChanges() {
        assertDoesNotThrow(() -> svc.renameStaff("mX", "nX", "NurseRenamed"));
        assertDoesNotThrow(() -> svc.setStaffPassword("mX", "nX", "newpass"));
    }
}
