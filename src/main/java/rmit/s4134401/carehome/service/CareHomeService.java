package rmit.s4134401.carehome.service;

import rmit.s4134401.carehome.*;
import rmit.s4134401.carehome.repo.*;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;

import rmit.s4134401.carehome.repo.PrescriptionRepository;
import rmit.s4134401.carehome.repo.AdministrationRepository;
import rmit.s4134401.carehome.Prescription;
import rmit.s4134401.carehome.MedicationAdministration;

public class CareHomeService {

	public static final LocalTime SHIFT_A_START = LocalTime.of(8, 0);
	public static final LocalTime SHIFT_A_END = LocalTime.of(16, 0);
	public static final LocalTime SHIFT_B_START = LocalTime.of(14, 0);
	public static final LocalTime SHIFT_B_END = LocalTime.of(22, 0);

	private final StaffRepository staffRepo;
	private final BedRepository bedRepo;
	private final PatientRepository patientRepo;
	private final NurseRosterRepository nurseRepo;
	private final DoctorMinutesRepository docMinRepo;
	private final AuditRepository auditRepo;

	private final PrescriptionRepository rxRepo;
	private final AdministrationRepository adminRepo;

	public CareHomeService(StaffRepository staffRepo, BedRepository bedRepo, PatientRepository patientRepo,
			NurseRosterRepository nurseRepo, DoctorMinutesRepository docMinRepo, AuditRepository auditRepo,
			PrescriptionRepository rxRepo, AdministrationRepository adminRepo) {
		this.staffRepo = staffRepo;
		this.bedRepo = bedRepo;
		this.patientRepo = patientRepo;
		this.nurseRepo = nurseRepo;
		this.docMinRepo = docMinRepo;
		this.auditRepo = auditRepo;
		this.rxRepo = rxRepo;
		this.adminRepo = adminRepo;
	}

	public void addDoctor(String id, String name) {
		staffRepo.add(new Doctor(id, name));
		auditRepo.log(Instant.now(), id, ActionType.ADD_STAFF, "added DOCTOR " + id + " " + name);
	}

	public void addNurse(String id, String name) {
		staffRepo.add(new Nurse(id, name));
		auditRepo.log(Instant.now(), id, ActionType.ADD_STAFF, "added NURSE " + id + " " + name);
	}

	public void addManager(String id, String name) {
		staffRepo.add(new Manager(id, name));
		auditRepo.log(Instant.now(), id, ActionType.ADD_STAFF, "added MANAGER " + id + " " + name);
	}

	public void renameStaff(String managerId, String staffId, String newName) {
		staffRepo.rename(staffId, newName);
		auditRepo.log(Instant.now(), managerId, ActionType.RENAME_STAFF, "renamed " + staffId + " to " + newName);
	}

	public void setStaffPassword(String managerId, String staffId, String newPass) {
		staffRepo.setPassword(staffId, newPass);
		auditRepo.log(Instant.now(), managerId, ActionType.SET_PASSWORD, "set password for " + staffId);
	}

	public void setDoctorMinutes(String managerId, String doctorId, DayOfWeek day, int minutes) {
		ensureRole(managerId, "MANAGER");
		if (minutes < 0)
			throw new IllegalArgumentException("minutes >= 0");
		docMinRepo.upsertDoctorMinutes(doctorId, day, minutes);
		auditRepo.log(Instant.now(), managerId, ActionType.SET_DOCTOR_MINUTES,
				"set doctor minutes " + doctorId + " " + day + "=" + minutes);
	}

	private boolean doctorOk(DayOfWeek day) {
		return docMinRepo.getMinutes(day) >= 60;
	}

	public void assignNurseShift(String managerId, String nurseId, DayOfWeek day, boolean shiftA) {
		ensureRole(managerId, "MANAGER");
		LocalTime s = shiftA ? SHIFT_A_START : SHIFT_B_START;
		LocalTime e = shiftA ? SHIFT_A_END : SHIFT_B_END;
		long current = nurseRepo.totalHoursFor(nurseId, day);
		long add = (long) java.time.Duration.between(s, e).toHours();
		if (current + add > 8L)
			throw new RosterException("Assigning exceeds 8h on " + day);
		nurseRepo.addShift(nurseId, day, s, e);
		auditRepo.log(Instant.now(), managerId, ActionType.SHIFT_ASSIGN,
				"assigned " + nurseId + " " + day + " " + (shiftA ? "A" : "B"));
	}

	public void removeNurseShift(String managerId, String nurseId, DayOfWeek day, boolean shiftA) {
		ensureRole(managerId, "MANAGER");
		LocalTime s = shiftA ? SHIFT_A_START : SHIFT_B_START;
		LocalTime e = shiftA ? SHIFT_A_END : SHIFT_B_END;
		int removed = nurseRepo.removeShift(nurseId, day, s, e);
		if (removed == 0)
			throw new RosterException("Shift not found");
		auditRepo.log(Instant.now(), managerId, ActionType.SHIFT_REMOVE,
				"removed " + nurseId + " " + day + " " + (shiftA ? "A" : "B"));
	}

	private boolean nurseOnShift(String nurseId, DayOfWeek day, LocalTime t) {
		return nurseRepo.hasShift(nurseId, day, t);
	}

	public void admitPatient(String managerId, String patientId, String fullName, Gender gender, boolean isolation,
			String ward, int room, int bedNum) {
		ensureRole(managerId, "MANAGER");
		if (patientRepo.existsPatientId(patientId))
			throw new IllegalArgumentException("patient id exists: " + patientId);
		Integer bedId = bedRepo.findBedId(ward, room, bedNum).orElse(null);
		if (bedId == null)
			throw new IllegalArgumentException("unknown bed");
		if (patientRepo.findOccupantByBedId(bedId).isPresent())
			throw new RosterException("Bed occupied");

		patientRepo.add(patientId, fullName, gender, isolation);
		patientRepo.assignToBed(patientId, bedId);
		auditRepo.log(Instant.now(), managerId, ActionType.ADMIT,
		        "admitted " + patientId + " -> " + ward + "-R" + room + "-B" + bedNum);
		audit(managerId, "ADMIT",
		        "pid=" + patientId + " -> " + ward + "-R" + room + "-B" + bedNum);

		
	}

	public void movePatient(String nurseId, DayOfWeek day, LocalTime now, String fromWard, int fromRoom, int fromBedNum,
			String toWard, int toRoom, int toBedNum) {
		ensureRole(nurseId, "NURSE");
		ensureNurseRostered(nurseId, day, now);
		if (!nurseOnShift(nurseId, day, now))
			throw new AuthorizationException("Nurse not on shift");
		Integer fromId = bedRepo.findBedId(fromWard, fromRoom, fromBedNum).orElse(null);
		Integer toId = bedRepo.findBedId(toWard, toRoom, toBedNum).orElse(null);
		if (fromId == null || toId == null)
			throw new IllegalArgumentException("unknown bed");

		String pid = patientRepo.findOccupantByBedId(fromId).orElse(null);
		if (pid == null)
			throw new RosterException("Source bed vacant");
		if (patientRepo.findOccupantByBedId(toId).isPresent())
			throw new RosterException("Destination occupied");

		patientRepo.vacateBed(fromId);
		patientRepo.assignToBed(pid, toId);
		auditRepo.log(Instant.now(), nurseId, ActionType.MOVE, "moved " + pid + " from " + fromWard + "-R" + fromRoom
				+ "-B" + fromBedNum + " to " + toWard + "-R" + toRoom + "-B" + toBedNum + " @ " + day + " " + now);
		
		audit(nurseId, "MOVE",
		        "day=" + day + " time=" + now +
		        " " + fromWard + "-R" + fromRoom + "-B" + fromBedNum +
		        " -> " + toWard + "-R" + toRoom + "-B" + toBedNum);

	}

	public void checkCompliance() {
		DayOfWeek[] days = DayOfWeek.values();
		for (int i = 0; i < days.length; i++) {
			DayOfWeek d = days[i];
			boolean a = nurseRepo.dayCovered(d, SHIFT_A_START, SHIFT_A_END);
			boolean b = nurseRepo.dayCovered(d, SHIFT_B_START, SHIFT_B_END);
			if (!a || !b)
				throw new ComplianceException("Coverage missing on " + d + (a ? "" : " [A]") + (b ? "" : " [B]"));
		}
		for (int i = 0; i < days.length; i++) {
			DayOfWeek d = days[i];
			if (!doctorOk(d))
				throw new ComplianceException("Doctor <60 mins on " + d);
		}
		auditRepo.log(Instant.now(), "system", ActionType.COMPLIANCE_CHECK, "full system compliance checked");
	}

	public void doctorAddPrescription(String doctorId, String patientId, DayOfWeek day, String medicine, String dose,
			String times) {
		ensureRole(doctorId, "DOCTOR");
		ensureDoctorMinutes(doctorId, day, 60);
		if (!doctorOk(day))
			throw new AuthorizationException("Doctor not rostered \u226560 mins today");
		int rxId = rxRepo.createPrescription(patientId, doctorId, day, java.time.Instant.now());
		rxRepo.addLine(rxId, medicine, dose, times);
		auditRepo.log(java.time.Instant.now(), doctorId, ActionType.RX_ADD,
				"RX for " + patientId + " on " + day + ": " + medicine + " " + dose + " @ " + times);
		audit(doctorId, "RX_ADD",
		        "pid=" + patientId + " day=" + day + " " + medicine + " " + dose + " @ " + times);

	}

	public java.util.List<Prescription> loadPrescriptionsForPatient(String patientId) {
		return rxRepo.loadForPatient(patientId);
	}

	public String patientIdInBed(String ward, int room, int bedNum) {
		Integer bid = bedRepo.findBedId(ward, room, bedNum).orElse(null);
		if (bid == null)
			throw new IllegalArgumentException("Unknown bed");
		return patientRepo.findOccupantByBedId(bid).orElse(null);
	}

	public void administerMedication(String nurseId, DayOfWeek day, LocalTime time, String patientId, String medicine,
			String dose) {
		ensureRole(nurseId, "NURSE");
		if (!nurseOnShift(nurseId, day, time))
		    throw new AuthorizationException("Nurse not on shift");
		adminRepo.add(patientId, medicine, dose, day, time, nurseId, false);
		auditRepo.log(java.time.Instant.now(), nurseId, ActionType.MED_ADMIN,
				"admin " + medicine + " " + dose + " to " + patientId + " @ " + day + " " + time);
		audit(nurseId, "ADMINISTER",
		        "pid=" + patientId + " day=" + day + " time=" + time + " " + medicine + " " + dose);
	}

	public void updateAdministrationDose(String staffId, DayOfWeek day, LocalTime atTime, String patientId,
			String medicine, String newDose, boolean isDoctor) {
		if (isDoctor) {
			if (!doctorOk(day))
				throw new AuthorizationException("Doctor not rostered today");
		} else {
			if (!nurseOnShift(staffId, day, atTime))
				throw new AuthorizationException("Nurse not on shift");
		}
		adminRepo.add(patientId, medicine, newDose, day, atTime, staffId, true);
		auditRepo.log(java.time.Instant.now(), staffId, ActionType.MED_UPDATE,
				"update " + patientId + " " + medicine + " @ " + day + " " + atTime + " -> " + newDose);
		audit(staffId, "MED_UPDATE",
		        "pid=" + patientId + " " + medicine + " @ " + day + " " + atTime + " -> " + newDose);

	}

	public java.util.List<MedicationAdministration> administrationsForPatient(String patientId) {
		return adminRepo.listForPatient(patientId);
	}
	
	private void audit(String staffId, String action, String details) {
	    try (var c = rmit.s4134401.carehome.util.DB.get();
	         var ps = c.prepareStatement("INSERT INTO audit(ts, staff_id, action, details) VALUES(?,?,?,?)")) {
	        ps.setString(1, java.time.OffsetDateTime.now().toString());
	        ps.setString(2, staffId);
	        ps.setString(3, action);
	        ps.setString(4, details);
	        ps.executeUpdate();
	    } catch (Exception ignore) { }
	}

	private String staffRoleOf(String staffId) {
	    try (var c = rmit.s4134401.carehome.util.DB.get();
	         var ps = c.prepareStatement("SELECT role FROM staff WHERE id=?")) {
	        ps.setString(1, staffId);
	        try (var rs = ps.executeQuery()) { return rs.next() ? rs.getString(1) : null; }
	    } catch (Exception e) { return null; }
	}

	private void ensureRole(String staffId, String requiredRole) {
	    String have = staffRoleOf(staffId);
	    if (!requiredRole.equals(have)) {
	        throw new NotAuthorisedException(requiredRole + " only");
	    }
	}

	private void ensureNurseRostered(String nurseId, java.time.DayOfWeek day, java.time.LocalTime time) {
	    String t = time.toString(); 
	    String sql = """
	        SELECT 1 FROM nurse_shifts
	        WHERE nurse_id=? AND day=? AND ? >= start AND ? <= end
	        LIMIT 1
	        """;
	    try (var c = rmit.s4134401.carehome.util.DB.get();
	         var ps = c.prepareStatement(sql)) {
	        ps.setString(1, nurseId);
	        ps.setString(2, day.name());
	        ps.setString(3, t);
	        ps.setString(4, t);
	        try (var rs = ps.executeQuery()) {
	            if (!rs.next()) throw new NotRosteredException("Nurse not rostered at " + day + " " + t);
	        }
	    } catch (NotRosteredException e) { throw e; }
	      catch (Exception e) { throw new NotRosteredException("Roster check failed"); }
	}

	private void ensureDoctorMinutes(String doctorId, java.time.DayOfWeek day, int requiredMinutes) {
	    String sql = "SELECT COALESCE(SUM(minutes),0) FROM doctor_minutes WHERE doctor_id=? AND day=?";
	    try (var c = rmit.s4134401.carehome.util.DB.get();
	         var ps = c.prepareStatement(sql)) {
	        ps.setString(1, doctorId);
	        ps.setString(2, day.name());
	        try (var rs = ps.executeQuery()) {
	            int mins = rs.next() ? rs.getInt(1) : 0;
	            if (mins < requiredMinutes) throw new NotRosteredException("Doctor not rostered enough on " + day);
	        }
	    } catch (NotRosteredException e) { throw e; }
	      catch (Exception e) { throw new NotRosteredException("Doctor roster check failed"); }
	}


}
