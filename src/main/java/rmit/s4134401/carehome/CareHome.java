package rmit.s4134401.carehome;

import java.io.*;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CareHome implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Map<String, Staff> staff = new HashMap<String, Staff>();
    final List<Bed> beds = new ArrayList<Bed>(); 
    private final List<ActionLog> audit = new ArrayList<ActionLog>();

    private final Map<String, List<Shift>> nurseRoster = new HashMap<String, List<Shift>>();
    private final Map<DayOfWeek, Integer> doctorDailyMinutes = new HashMap<DayOfWeek, Integer>();

    private final Map<String, List<Prescription>> prescriptionsByPatient = new HashMap<String, List<Prescription>>();
    private final List<MedicationAdministration> administrations = new ArrayList<MedicationAdministration>();

    public static final LocalTime SHIFT_A_START = LocalTime.of(8,0);
    public static final LocalTime SHIFT_A_END   = LocalTime.of(16,0);
    public static final LocalTime SHIFT_B_START = LocalTime.of(14,0);
    public static final LocalTime SHIFT_B_END   = LocalTime.of(22,0);

    public CareHome(){
        String[] wards = {"A","B"};
        for (int w=0; w<wards.length; w++){
            String ward = wards[w];
            for (int room=1; room<=6; room++){
                int maxBeds;
                if (room==1 || room==2) maxBeds = 1;
                else if (room==3 || room==4) maxBeds = 2;
                else if (room==5) maxBeds = 3;
                else maxBeds = 4;
                for (int b=1; b<=maxBeds; b++) beds.add(new Bed(ward, room, b));
            }
        }
        DayOfWeek[] days = DayOfWeek.values();
        for (int i=0; i<days.length; i++) doctorDailyMinutes.put(days[i], Integer.valueOf(0));
    }

    public void addManager(String id, String name){ putStaff(new Manager(id,name)); }
    public void addDoctor (String id, String name){ putStaff(new Doctor (id,name)); }
    public void addNurse  (String id, String name){ putStaff(new Nurse  (id,name)); }

    private void putStaff(Staff s){
        if (staff.containsKey(s.id())) throw new IllegalArgumentException("staff id exists: " + s.id());
        staff.put(s.id(), s);
        if (s.role()==Role.NURSE) nurseRoster.put(s.id(), new ArrayList<Shift>());
        log(s.id(), ActionType.ADD_STAFF, "added " + s);
    }

    public void removeNurseShift(String managerId, String nurseId, DayOfWeek day, boolean shiftA){
        requireRole(managerId, Role.MANAGER);
        Staff target = requireStaff(nurseId, Role.NURSE);
        LocalTime s = shiftA?SHIFT_A_START:SHIFT_B_START;
        LocalTime e = shiftA?SHIFT_A_END:SHIFT_B_END;

        List<Shift> list = nurseRoster.get(target.id());
        if (list == null || list.isEmpty()) throw new RosterException("No shifts to remove for nurse " + nurseId);
        boolean removed = false;
        for (int i=0;i<list.size();i++){
            Shift sh = list.get(i);
            if (sh.day == day && sh.start.equals(s) && sh.end.equals(e)) {
                list.remove(i);
                removed = true;
                break;
            }
        }
        if (!removed) throw new RosterException("Shift not found for removal");
        log(managerId, ActionType.SHIFT_REMOVE, "removed shift " + (shiftA?"A":"B") + " on " + day + " for " + nurseId);
    }

    public void renameStaff(String managerId, String staffId, String newName){
        requireRole(managerId, Role.MANAGER);
        Staff s = staff.get(staffId);
        if (s == null) throw new IllegalArgumentException("unknown staff: " + staffId);
        s.rename(newName);
        log(managerId, ActionType.RENAME_STAFF, "renamed staff " + staffId + " to " + newName);
    }

    public void setStaffPassword(String managerId, String staffId, String newPassword){
        requireRole(managerId, Role.MANAGER);
        Staff s = staff.get(staffId);
        if (s == null) throw new IllegalArgumentException("unknown staff: " + staffId);
        s.setPassword(newPassword);
        log(managerId, ActionType.SET_PASSWORD, "set password for staff " + staffId);
    }

    public Patient viewResident(String staffId, Bed bed){
        Staff s = staff.get(staffId);
        if (s == null) throw new IllegalArgumentException("unknown staff: " + staffId);
        if (s.role() != Role.DOCTOR && s.role() != Role.NURSE)
            throw new AuthorizationException("Only medical staff can view resident details");
        if (bed.isVacant()) throw new RosterException("Bed is vacant");
        Patient p = bed.getOccupant();
        return p;
    }

    private boolean isDoctorSufficientlyRostered(DayOfWeek day){
        Integer mins = doctorDailyMinutes.get(day);
        return mins != null && mins.intValue() >= 60;
    }

    public void doctorAddPrescription(String doctorId, Bed bed, DayOfWeek day, Prescription rx){
        Staff s = requireStaff(doctorId, Role.DOCTOR);
        if (!isDoctorSufficientlyRostered(day)) throw new AuthorizationException("Doctor not rostered ≥60 mins today");
        if (bed.isVacant()) throw new RosterException("Bed is vacant");
        Patient p = bed.getOccupant();
        if (!p.id().equals(rx.patientId())) throw new IllegalArgumentException("RX patient mismatch");
        List<Prescription> list = prescriptionsByPatient.get(p.id());
        if (list == null){ list = new ArrayList<Prescription>(); prescriptionsByPatient.put(p.id(), list); }
        list.add(rx);
        log(doctorId, ActionType.RX_ADD, "RX for " + p.id() + " on " + day + ": " + rx.toString());
    }

    public void administerMedication(String staffId, Bed bed, DayOfWeek day, LocalTime time,
                                     String medicine, String dose) {
        Staff s = staff.get(staffId);
        if (s == null) throw new IllegalArgumentException("unknown staff: " + staffId);
        if (bed.isVacant()) throw new RosterException("Bed is vacant");

        if (s.role() != Role.NURSE) throw new AuthorizationException("Only nurses can administer medication");
        if (!isNurseOnShift(staffId, day, time)) throw new AuthorizationException("Nurse not on shift");

        Patient p = bed.getOccupant();
        MedicationAdministration admin = new MedicationAdministration(p.id(), medicine, dose, day, time, staffId);
        administrations.add(admin);
        log(staffId, ActionType.MED_ADMIN, "admin " + medicine + " " + dose + " to " + p.id() + " @ " + day + " " + time);
    }

    public void printPrescriptionsForPatient(String patientId){
        System.out.println("--- Prescriptions for " + patientId + " ---");
        List<Prescription> list = prescriptionsByPatient.get(patientId);
        if (list == null || list.isEmpty()) { System.out.println("(none)"); return; }
        for (int i=0;i<list.size();i++) System.out.println((i+1) + ") " + list.get(i));
    }

    public void printAdministrations(){
        System.out.println("--- Medication administrations ---");
        if (administrations.isEmpty()) { System.out.println("(none)"); return; }
        for (int i=0;i<administrations.size();i++) System.out.println((i+1)+") "+administrations.get(i));
    }

    public void assignNurseShift(String managerId, String nurseId, DayOfWeek day, boolean shiftA){
        requireRole(managerId, Role.MANAGER);
        Staff target = requireStaff(nurseId, Role.NURSE);
        Shift newShift = new Shift(day, shiftA?SHIFT_A_START:SHIFT_B_START, shiftA?SHIFT_A_END:SHIFT_B_END);

        List<Shift> shifts = nurseRoster.get(target.id());
        if (shifts == null) { shifts = new ArrayList<Shift>(); nurseRoster.put(target.id(), shifts); }
        long hours = 0;
        for (int i=0;i<shifts.size();i++){
            Shift s = shifts.get(i);
            if (s.day == day) hours += s.hours();
        }
        if (hours + newShift.hours() > 8L) {
            throw new RosterException("Assigning this shift exceeds 8 hours for " + nurseId + " on " + day);
        }

        shifts.add(newShift);
        log(managerId, ActionType.SHIFT_ASSIGN, "assigned " + target + " to " + newShift);
    }

    public void setDoctorMinutes(String managerId, DayOfWeek day, int minutes){
        requireRole(managerId, Role.MANAGER);
        if (minutes < 0) throw new IllegalArgumentException("minutes must be >= 0");
        doctorDailyMinutes.put(day, Integer.valueOf(minutes));
        log(managerId, ActionType.SET_DOCTOR_MINUTES, "set doctor minutes on " + day + " = " + minutes);
    }

    public Bed findFirstVacant(){
        for (int i=0; i<beds.size(); i++){
            Bed b = beds.get(i);
            if (b.isVacant()) return b;
        }
        return null;
    }

    public void admitPatient(String managerId, Patient p, Bed target){
        requireRole(managerId, Role.MANAGER);
        if (p == null) throw new IllegalArgumentException("null patient");
        if (target == null) throw new IllegalArgumentException("null bed");
        if (isPatientIdInUse(p.id())) throw new IllegalArgumentException("patient id exists: " + p.id());
        if (!target.isVacant()) throw new RosterException("Bed occupied: " + target);
        if (!canPlace(p, target)) throw new RosterException("Placement rule violation (gender/isolation)");
        target.assign(p);
        log(managerId, ActionType.ADMIT, "admitted " + p + " to " + target);
    }

    public void movePatient(String nurseId, Bed from, Bed to, DayOfWeek day, LocalTime now){
        requireRole(nurseId, Role.NURSE);
        if (from.isVacant()) throw new RosterException("Source bed is vacant");
        if (!to.isVacant()) throw new RosterException("Destination bed occupied");
        if (!isNurseOnShift(nurseId, day, now)) throw new AuthorizationException("Nurse not on shift now");
        Patient p = from.getOccupant();
        if (p == null) throw new RosterException("No occupant in source bed");
        if (!canPlace(p, to)) throw new RosterException("Placement rule violation (gender/isolation)");
        from.vacate(); to.assign(p);
        log(nurseId, ActionType.MOVE, "moved " + p.id() + " from " + from + " to " + to + " on " + day + " " + now);
    }

    private boolean isNurseOnShift(String nurseId, DayOfWeek day, LocalTime t){
        List<Shift> list = nurseRoster.get(nurseId);
        if (list == null) return false;
        for (int i=0; i<list.size(); i++){
            Shift s = list.get(i);
            if (s.day == day && !t.isBefore(s.start) && t.isBefore(s.end)) return true;
        }
        return false;
    }

    public void checkCompliance(){
        DayOfWeek[] days = DayOfWeek.values();

        for (int di=0; di<days.length; di++){
            DayOfWeek d = days[di];
            boolean aCovered = false;
            boolean bCovered = false;

            for (Map.Entry<String, List<Shift>> e : nurseRoster.entrySet()){
                List<Shift> ls = e.getValue();
                for (int i=0; i<ls.size(); i++){
                    Shift s = ls.get(i);
                    if (s.day == d && s.start.equals(SHIFT_A_START) && s.end.equals(SHIFT_A_END)) aCovered = true;
                    if (s.day == d && s.start.equals(SHIFT_B_START) && s.end.equals(SHIFT_B_END)) bCovered = true;
                }
            }

            if (!aCovered || !bCovered){
                String missing = "";
                if (!aCovered) missing += " [Shift A]";
                if (!bCovered) missing += " [Shift B]";
                throw new ComplianceException("Coverage missing on " + d + missing);
            }
        }

        for (Map.Entry<String, List<Shift>> e : nurseRoster.entrySet()){
            String nurse = e.getKey();
            Map<DayOfWeek, Long> dayHours = new HashMap<DayOfWeek, Long>();
            List<Shift> ls = e.getValue();
            for (int i=0; i<ls.size(); i++){
                Shift s = ls.get(i);
                Long old = dayHours.get(s.day);
                long add = s.hours();
                if (old == null) dayHours.put(s.day, Long.valueOf(add));
                else dayHours.put(s.day, Long.valueOf(old.longValue() + add));
            }
            for (Map.Entry<DayOfWeek, Long> dh : dayHours.entrySet()){
                if (dh.getValue().longValue() > 8L)
                    throw new ComplianceException("Nurse " + nurse + " exceeds 8h on " + dh.getKey());
            }
        }

        DayOfWeek[] days2 = DayOfWeek.values();
        for (int di=0; di<days2.length; di++){
            DayOfWeek d = days2[di];
            if (!isDoctorSufficientlyRostered(d)) {
                throw new ComplianceException("Doctor <60 mins on " + d);
            }
        }

        log("system", ActionType.COMPLIANCE_CHECK, "full system compliance checked");
    }

    public void saveToFile(String path){
        try {
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(path));
            oos.writeObject(this);
            oos.close();
        } catch (IOException e){
            throw new RuntimeException("save failed: " + e.getMessage(), e);
        }
    }

    public static CareHome loadFromFile(String path){
        try {
            ObjectInputStream ois = new ObjectInputStream(new FileInputStream(path));
            CareHome obj = (CareHome) ois.readObject();
            ois.close();
            return obj;
        } catch (IOException e){
            throw new RuntimeException("load failed: " + e.getMessage(), e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("load failed: " + e.getMessage(), e);
        }
    }

    private Staff requireStaff(String id, Role role){
        Staff s = staff.get(id);
        if (s == null) throw new IllegalArgumentException("unknown staff: " + id);
        if (s.role() != role) throw new AuthorizationException("staff role mismatch: required " + role + ", was " + s.role());
        return s;
    }
    private void requireRole(String staffId, Role role){ requireStaff(staffId, role); }

    private void log(String staffId, ActionType type, String details){
        audit.add(new ActionLog(Instant.now(), staffId, type, details));
    }
    @SuppressWarnings("unused")
    private void log(String staffId, String details){
        audit.add(new ActionLog(Instant.now(), staffId, ActionType.COMPLIANCE_CHECK, details));
    }

    public void updateAdministrationDose(String staffId,
                                         String patientId,
                                         String medicine,
                                         DayOfWeek day,
                                         LocalTime atTime,
                                         String newDose) {
        Staff s = staff.get(staffId);
        if (s == null) throw new IllegalArgumentException("unknown staff: " + staffId);
        if (s.role() == Role.NURSE) {
            if (!isNurseOnShift(staffId, day, atTime)) throw new AuthorizationException("Nurse not on shift");
        } else if (s.role() == Role.DOCTOR) {
            if (!isDoctorSufficientlyRostered(day)) throw new AuthorizationException("Doctor not rostered today");
        } else {
            throw new AuthorizationException("Only medical staff can update administration details");
        }
        administrations.add(new MedicationAdministration(patientId, medicine, newDose, day, atTime, staffId));
        log(staffId, ActionType.MED_UPDATE, "update " + patientId + " " + medicine + " @ " + day + " " + atTime + " -> " + newDose);
    }

    private boolean isPatientIdInUse(String pid){
        for (int i=0; i<beds.size(); i++){
            Bed b = beds.get(i);
            if (!b.isVacant() && b.getOccupant().id().equals(pid)) return true;
        }
        return false;
    }

    public void printAudit() {
        System.out.println("--- Audit log ---");
        for (int i = 0; i < audit.size(); i++) {
            System.out.println((i+1) + ") " + audit.get(i));
        }
    }

    private static void printBeds(CareHome app){
        System.out.println("--- Beds ---");
        for (int i = 0; i < app.beds.size(); i++){
            System.out.println(i + ": " + app.beds.get(i).toString());
        }
    }

    private static int askBedIndex(Scanner sc, String prompt, int max){
        System.out.print(prompt + " (index number): ");
        String raw = sc.nextLine().trim();
        try {
            int idx = Integer.parseInt(raw);
            if (idx < 0 || idx >= max) throw new IllegalArgumentException("invalid bed index");
            return idx;
        } catch (NumberFormatException nfe){
            throw new IllegalArgumentException("please enter a number");
        }
    }

    public void printAdministrationsForPatient(String patientId){
        System.out.println("--- Administrations for " + patientId + " ---");
        int count = 0;
        for (int i=0;i<administrations.size();i++){
            MedicationAdministration m = administrations.get(i);
            if (m.patientId().equals(patientId)){
                count++;
                System.out.println(count + ") " + m);
            }
        }
        if (count==0) System.out.println("(none)");
    }



    private int roomCapacityFor(int room){
        if (room==1 || room==2) return 1;
        if (room==3 || room==4) return 2;
        if (room==5) return 3;
        return 4; 
    }

    private boolean sameRoom(Bed a, Bed b){
        return a.ward().equals(b.ward()) && a.room() == b.room();
    }

    private boolean canPlace(Patient p, Bed target){
        if (!target.isVacant()) return false;
        int cap = roomCapacityFor(target.room());

        if (p.needsIsolation() && cap > 1) return false;

        if (cap > 1){
            for (int i=0; i<beds.size(); i++){
                Bed b = beds.get(i);
                if (sameRoom(target, b) && !b.isVacant()){
                    if (b.getOccupant().gender() != p.gender()) return false;
                }
            }
        }
        return true;
    }

    public Map<Role, Long> staffCounts() {
        return staff.values().stream()
                .collect(Collectors.groupingBy(Staff::role, Collectors.counting()));
    }

    public void printAllStaff() {
        System.out.println("--- Staff ---");
        if (staff.isEmpty()) { System.out.println("(none)"); return; }
        staff.values().forEach(System.out::println);
    }

    public List<Shift> getNurseRoster(String nurseId) {
        requireStaff(nurseId, Role.NURSE);
        return Collections.unmodifiableList(
                nurseRoster.getOrDefault(nurseId, Collections.emptyList()));
    }

    public void printRosterFor(String nurseId) {
        requireStaff(nurseId, Role.NURSE);
        System.out.println("--- Roster for " + nurseId + " ---");
        List<Shift> ls = nurseRoster.getOrDefault(nurseId, Collections.emptyList());
        if (ls.isEmpty()) System.out.println("(none)"); else ls.forEach(System.out::println);
    }

    public void printAuditForStaff(String staffId) {
        System.out.println("--- Audit for " + staffId + " ---");
        boolean any = false;
        for (ActionLog a : audit) {
            if (staffId.equals(a.staffId)) { System.out.println(a); any = true; }
        }
        if (!any) System.out.println("(none)");
    }


    public String prescriptionsSummary(String patientId){
        StringBuilder sb = new StringBuilder();
        java.util.List<Prescription> list = prescriptionsByPatient.get(patientId);
        if (list == null || list.isEmpty()) return "(no prescriptions)";
        for (int i=0;i<list.size();i++){
            sb.append(i+1).append(") ").append(list.get(i).toString()).append("\n");
        }
        return sb.toString().trim();
    }

    public String administrationsSummary(String patientId){
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (int i=0;i<administrations.size();i++){
            MedicationAdministration m = administrations.get(i);
            if (m.patientId().equals(patientId)){
                count++;
                sb.append(count).append(") ").append(m.toString()).append("\n");
            }
        }
        return count==0 ? "(no administrations)" : sb.toString().trim();
    }

    public Bed findBedByPatientId(String pid){
        for (int i=0;i<beds.size();i++){
            Bed b = beds.get(i);
            if (!b.isVacant() && b.getOccupant().id().equals(pid)) return b;
        }
        return null;
    }

    public String nurseRosterSummary(String nurseId){
        requireStaff(nurseId, Role.NURSE);
        java.util.List<Shift> ls = nurseRoster.getOrDefault(nurseId, java.util.Collections.emptyList());
        if (ls.isEmpty()) return "(none)";
        StringBuilder sb = new StringBuilder();
        for (Shift s : ls){
            String label = (s.start.equals(SHIFT_A_START) && s.end.equals(SHIFT_A_END)) ? "A" : "B";
            sb.append(s.day).append("  Shift ").append(label)
              .append(" (").append(s.start).append("-").append(s.end).append(")\n");
        }
        return sb.toString().trim();
    }

    public String nurseWeeklyRosterSummary(){
        StringBuilder sb = new StringBuilder();
        for (DayOfWeek d : DayOfWeek.values()){
            sb.append("== ").append(d).append(" ==\n");
            boolean any = false;
            for (java.util.Map.Entry<String, java.util.List<Shift>> e : nurseRoster.entrySet()){
                String nid = e.getKey();
                for (Shift s : e.getValue()){
                    if (s.day == d){
                        any = true;
                        String label = (s.start.equals(SHIFT_A_START) && s.end.equals(SHIFT_A_END)) ? "A" : "B";
                        sb.append(" • ").append(nid)
                          .append("  Shift ").append(label)
                          .append(" (").append(s.start).append("-").append(s.end).append(")\n");
                    }
                }
            }
            if (!any) sb.append(" (no nurse on roster)\n");
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    public String doctorMinutesSummary(){
        StringBuilder sb = new StringBuilder("--- Doctor coverage (minutes per day) ---\n");
        for (DayOfWeek d : DayOfWeek.values()){
            int mins = doctorDailyMinutes.getOrDefault(d, 0);
            sb.append(String.format(" %s : %d min %s\n",
                    d, mins, (mins >= 60 ? "[OK]" : "[Below 60]")));
        }
        return sb.toString().trim();
    }

    public String teamRosterSummary(){
        StringBuilder sb = new StringBuilder();
        sb.append(doctorMinutesSummary()).append("\n\n");
        sb.append("--- Nurse roster (week) ---\n");
        sb.append(nurseWeeklyRosterSummary());
        return sb.toString();
    }
    
    public String allNursesRosterByPerson(){
        StringBuilder sb = new StringBuilder("--- Nurse roster (per person, whole week) ---\n");
        if (nurseRoster.isEmpty()) {
            sb.append("(no nurses added)\n");
            return sb.toString();
        }
        java.util.List<String> nurseIds = new java.util.ArrayList<>(nurseRoster.keySet());
        java.util.Collections.sort(nurseIds);
        for (String nid : nurseIds){
            sb.append("\n[").append(nid).append("]\n");
            java.util.List<Shift> ls = nurseRoster.getOrDefault(nid, java.util.Collections.emptyList());
            if (ls.isEmpty()){
                sb.append("  (no shifts)\n");
                continue;
            }
            ls.stream()
              .sorted((a,b) -> {
                  int c = a.day.compareTo(b.day);
                  if (c != 0) return c;
                  return a.start.compareTo(b.start);
              })
              .forEach(s -> {
                  String label = (s.start.equals(SHIFT_A_START) && s.end.equals(SHIFT_A_END)) ? "A" : "B";
                  sb.append("  ").append(s.day)
                    .append("  Shift ").append(label)
                    .append(" (").append(s.start).append("-").append(s.end).append(")\n");
              });
        }
        return sb.toString().trim();
    }

    public static void main(String[] args){
        CareHome app = new CareHome();
        Scanner sc = new Scanner(System.in);
        System.out.println("CareHome — Phase 1 demo. Type number and ENTER.\n");
        boolean running = true;

        while(running){
            System.out.println("\n1) Add staff  2) Set/Modify staff  3) Set doctor minutes");
            System.out.println("4) Assign nurse shift  5) Remove nurse shift  6) Admit patient");
            System.out.println("7) View resident  8) Add prescription  9) Administer medication");
            System.out.println("10) Move resident  11) Check compliance  12) Update admin dose");
            System.out.println("13) Show audit  14) List beds  15) List patients' admins");
            System.out.println("0) Exit");
            System.out.print("> ");
            String choice = sc.nextLine().trim();
            try{
                if ("1".equals(choice)) {
                    System.out.print("Role (D/N): "); String r = sc.nextLine().trim().toUpperCase();
                    System.out.print("Staff id: "); String sid = sc.nextLine().trim();
                    System.out.print("Name: "); String nm = sc.nextLine().trim();
                    if ("D".equals(r)) app.addDoctor(sid, nm);
                    else if ("N".equals(r)) app.addNurse(sid, nm);
                    else System.out.println("Only D or N");
                }
                else if ("2".equals(choice)) {
                    System.out.print("Manager id: "); String mid = sc.nextLine().trim();
                    System.out.print("Staff id: "); String sid = sc.nextLine().trim();
                    System.out.print("Modify (N=Name, P=Password): "); String which = sc.nextLine().trim().toUpperCase();
                    if ("N".equals(which)) {
                        System.out.print("New name: "); String nm = sc.nextLine().trim();
                        app.renameStaff(mid, sid, nm);
                        System.out.println("Name updated.");
                    } else if ("P".equals(which)) {
                        System.out.print("New password: "); String pw = sc.nextLine().trim();
                        app.setStaffPassword(mid, sid, pw);
                        System.out.println("Password set.");
                    } else {
                        System.out.println("Only N or P.");
                    }
                }
                else if ("3".equals(choice)) {
                    System.out.print("Manager id: "); String mid = sc.nextLine().trim();
                    DayOfWeek day = askDay(sc);
                    System.out.print("Minutes (>=60 recommended): "); int min = Integer.parseInt(sc.nextLine().trim());
                    app.setDoctorMinutes(mid, day, min);
                    System.out.println("Set.");
                }
                else if ("4".equals(choice)) {
                    System.out.print("Manager id: "); String mid = sc.nextLine().trim();
                    System.out.print("Nurse id: "); String nid = sc.nextLine().trim();
                    DayOfWeek day = askDay(sc);
                    boolean isA = askShift(sc);
                    app.assignNurseShift(mid, nid, day, isA);
                    System.out.println("Assigned.");
                }
                else if ("5".equals(choice)) {
                    System.out.print("Manager id: "); String mid = sc.nextLine().trim();
                    System.out.print("Nurse id: "); String nid = sc.nextLine().trim();
                    DayOfWeek day = askDay(sc);
                    boolean isA = askShift(sc);
                    app.removeNurseShift(mid, nid, day, isA);
                    System.out.println("Removed.");
                }
                else if ("6".equals(choice)) {
                    System.out.print("Manager id: "); String mid = sc.nextLine().trim();
                    printBeds(app);
                    int idx = askBedIndex(sc, "Choose a VACANT bed", app.beds.size());
                    Bed v = app.beds.get(idx);
                    if (!v.isVacant()) { System.out.println("That bed is occupied."); }
                    else {
                        System.out.print("Patient id: "); String pid = sc.nextLine().trim();
                        System.out.print("Patient name: "); String pnm = sc.nextLine().trim();
                        System.out.print("Gender (M/F): "); Gender g = Gender.valueOf(sc.nextLine().trim().toUpperCase());
                        System.out.print("Isolation (true/false): "); boolean iso = Boolean.parseBoolean(sc.nextLine().trim());
                        app.admitPatient(mid, new Patient(pid,pnm,g,iso), v);
                        System.out.println("Admitted to " + v);
                    }
                }
                else if ("7".equals(choice)) {
                    System.out.print("Medical staff id (doctor/nurse): "); String sid = sc.nextLine().trim();
                    printBeds(app);
                    int idx = askBedIndex(sc, "Choose an OCCUPIED bed to view", app.beds.size());
                    Bed target = app.beds.get(idx);
                    if (target.isVacant()) System.out.println("Bed is vacant.");
                    else System.out.println("Resident: " + app.viewResident(sid, target));
                }
                else if ("8".equals(choice)) {
                    System.out.print("Doctor id: "); String did = sc.nextLine().trim();
                    DayOfWeek day = askDay(sc);
                    printBeds(app);
                    int idx = askBedIndex(sc, "Choose an OCCUPIED bed for RX", app.beds.size());
                    Bed target = app.beds.get(idx);
                    if (target.isVacant()) System.out.println("No resident in that bed.");
                    else {
                        Patient p = target.getOccupant();
                        Prescription rx = new Prescription(p.id());
                        System.out.print("Medicine: "); String med = sc.nextLine().trim();
                        System.out.print("Dose: "); String dose = sc.nextLine().trim();
                        System.out.print("Times: "); String times = sc.nextLine().trim();
                        rx.addLine(med, dose, times);
                        app.doctorAddPrescription(did, target, day, rx);
                        System.out.println("Prescription added to " + target);
                    }
                }
                else if ("9".equals(choice)) {
                    System.out.print("Nurse id: "); String sid = sc.nextLine().trim();
                    DayOfWeek day = askDay(sc);
                    System.out.print("Hour (0-23): "); int hh = Integer.parseInt(sc.nextLine().trim());
                    LocalTime t = LocalTime.of(hh,0);
                    printBeds(app);
                    int idx = askBedIndex(sc, "Choose an OCCUPIED bed to administer", app.beds.size());
                    Bed target = app.beds.get(idx);
                    if (target.isVacant()) System.out.println("Bed is vacant.");
                    else {
                        System.out.print("Medicine: "); String med = sc.nextLine().trim();
                        System.out.print("Dose: "); String dose = sc.nextLine().trim();
                        app.administerMedication(sid, target, day, t, med, dose);
                        System.out.println("Administered to " + target);
                    }
                }
                else if ("10".equals(choice)) {
                    System.out.print("Nurse id: "); String nid = sc.nextLine().trim();
                    DayOfWeek day = askDay(sc);
                    System.out.print("Hour (0-23): "); int hh = Integer.parseInt(sc.nextLine().trim());
                    LocalTime now = LocalTime.of(hh,0);
                    printBeds(app);
                    int fromIdx = askBedIndex(sc, "Choose FROM OCCUPIED bed", app.beds.size());
                    int toIdx   = askBedIndex(sc, "Choose TO VACANT bed", app.beds.size());
                    Bed from = app.beds.get(fromIdx);
                    Bed to   = app.beds.get(toIdx);
                    if (from.isVacant()) System.out.println("FROM bed is vacant.");
                    else if (!to.isVacant()) System.out.println("TO bed is occupied.");
                    else {
                        Patient moved = from.getOccupant();
                        app.movePatient(nid, from, to, day, now);
                        System.out.println("Moved " + moved + " from " + from + " to " + to + " at " + day + " " + now);
                    }
                }
                else if ("11".equals(choice)) {
                    app.checkCompliance();
                    System.out.println("Compliance OK.");
                }
                else if ("12".equals(choice)) {
                    System.out.print("Staff id (doctor/nurse): "); String sid = sc.nextLine().trim();
                    System.out.print("Patient id: "); String pid = sc.nextLine().trim();
                    app.printAdministrationsForPatient(pid);
                    System.out.print("Medicine to correct: "); String med = sc.nextLine().trim();
                    DayOfWeek day = askDay(sc);
                    System.out.print("Hour (0-23) of original admin: "); int hh = Integer.parseInt(sc.nextLine().trim());
                    LocalTime t = LocalTime.of(hh,0);
                    System.out.print("New dose: "); String newDose = sc.nextLine().trim();
                    app.updateAdministrationDose(sid, pid, med, day, t, newDose);
                    System.out.println("Administration updated (correction recorded).");
                }
                else if ("13".equals(choice)) {
                    app.printAudit();
                }
                else if ("14".equals(choice)) {
                    printBeds(app);
                }
                else if ("15".equals(choice)) {
                    System.out.print("Patient id: "); String pid = sc.nextLine().trim();
                    app.printAdministrationsForPatient(pid);
                }
                else if ("0".equals(choice)) {
                    running = false;
                }
                else {
                    System.out.println("Unknown option");
                }
            } catch (Exception ex){
                System.out.println("[ERROR] " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        }
        System.out.println("Bye.");
    }

    private static DayOfWeek askDay(Scanner sc){
        System.out.print("Day (MON..SUN): ");
        return DayOfWeek.valueOf(sc.nextLine().trim().toUpperCase());
    }
    private static boolean askShift(Scanner sc){
        System.out.print("Shift (A=08-16, B=14-22): ");
        String s = sc.nextLine().trim().toUpperCase();
        if ("A".equals(s)) return true;
        if ("B".equals(s)) return false;
        throw new IllegalArgumentException("A or B only");
    }
}
