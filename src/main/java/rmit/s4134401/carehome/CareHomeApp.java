package rmit.s4134401.carehome;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.time.DayOfWeek;
import java.time.LocalTime;

import rmit.s4134401.carehome.service.CareHomeService;
import rmit.s4134401.carehome.repo.jdbc.*;

public class CareHomeApp extends Application {

    private CareHomeService svc;
    private CareHome app = new CareHome();

    private final TilePane bedGrid = new TilePane();
    private final Text status = new Text("Ready");

    @Override
    public void start(Stage stage) {
        rmit.s4134401.carehome.util.DB.init("carehome.db"); 
        rmit.s4134401.carehome.util.SchemaMigrator.ensure();

        BorderPane root = new BorderPane();
        root.setTop(buildMenuBar(stage));
        root.setCenter(buildBedPane());
        root.setBottom(buildStatusBar());

        Scene scene = new Scene(root, 1000, 650);
        stage.setTitle("Resident HealthCare System — S2 2025");
        stage.setScene(scene);
        stage.show();

        svc = new CareHomeService(
                new JdbcStaffRepository(),
                new JdbcBedRepository(),
                new JdbcPatientRepository(),
                new JdbcNurseRosterRepository(),
                new JdbcDoctorMinutesRepository(),
                new JdbcAuditRepository(),
                new JdbcPrescriptionRepository(),
                new JdbcAdministrationRepository()
        );

        try { svc.addManager("m1", "Manager One"); } catch (RuntimeException ignore) {}

        refreshBeds();
    }
    
    private void showRosterDashboard(){
        TabPane tabs = new TabPane();

        TextArea team = new TextArea(app.teamRosterSummary());
        team.setEditable(false); team.setWrapText(true);
        team.setPrefSize(820, 520);
        Tab t1 = new Tab("Team (Week)", new ScrollPane(team));
        t1.setClosable(false);

        TextArea byDay = new TextArea(app.nurseWeeklyRosterSummary());
        byDay.setEditable(false); byDay.setWrapText(true);
        Tab t2 = new Tab("Nurses by Day", new ScrollPane(byDay));
        t2.setClosable(false);

        TextArea byPerson = new TextArea(app.allNursesRosterByPerson());
        byPerson.setEditable(false); byPerson.setWrapText(true);
        Tab t3 = new Tab("Nurses by Person", new ScrollPane(byPerson));
        t3.setClosable(false);

        TextArea docs = new TextArea(app.doctorMinutesSummary());
        docs.setEditable(false); docs.setWrapText(true);
        Tab t4 = new Tab("Doctor Coverage", new ScrollPane(docs));
        t4.setClosable(false);

        tabs.getTabs().addAll(t1, t2, t3, t4);

        Dialog<Void> d = new Dialog<>();
        d.setTitle("Roster Dashboard");
        d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        d.getDialogPane().setContent(tabs);
        d.setResizable(true);
        d.getDialogPane().setPrefSize(880, 600);
        d.showAndWait();

        setStatus("Roster Dashboard viewed");
    }

    
    private static final class BedCell {
        final int id;              
        final String ward;
        final int room;
        final int bedNum;
        final String patientId;    
        final Gender gender;       
        final boolean isolation;   

        BedCell(int id, String ward, int room, int bedNum,
                String patientId, Gender gender, boolean isolation){
            this.id = id; this.ward = ward; this.room = room; this.bedNum = bedNum;
            this.patientId = patientId; this.gender = gender; this.isolation = isolation;
        }
        boolean isVacant(){ return patientId == null; }
        String cellLabel(){
            String base = ward + "-R" + room + "-B" + bedNum;
            return isVacant() ? base + "\n[vacant]" : base + "\n" + patientId;
        }
    }

    private java.util.List<BedCell> loadBedCellsFromDB() {
        java.util.List<BedCell> out = new java.util.ArrayList<>();
        String sql =
            "SELECT b.id, b.ward, b.room, b.bed_num, p.id AS pid, p.gender, p.isolation " +
            "FROM beds b " +
            "LEFT JOIN patients p ON p.bed_id = b.id " +
            "ORDER BY b.ward, b.room, b.bed_num";
        try (java.sql.Connection c = rmit.s4134401.carehome.util.DB.get();
             java.sql.Statement st = c.createStatement();
             java.sql.ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                int id = rs.getInt("id");
                String ward = rs.getString("ward");
                int room = rs.getInt("room");
                int bedNum = rs.getInt("bed_num");
                String pid = rs.getString("pid"); 
                String gstr = rs.getString("gender");
                Gender g = (pid == null || gstr == null) ? null : Gender.valueOf(gstr.toUpperCase());
                boolean iso = (pid != null) && rs.getInt("isolation") == 1;
                out.add(new BedCell(id, ward, room, bedNum, pid, g, iso));
            }
        } catch (Exception e){
            showError("DB Error", e.getMessage());
        }
        return out;
    }

    private MenuBar buildMenuBar(Stage stage){
        Menu file = new Menu("File");
        MenuItem save = new MenuItem("Save…");
        save.setOnAction(e -> {
            try { app.saveToFile("carehome.bin"); setStatus("Saved to carehome.bin"); }
            catch (RuntimeException ex) { showError("Save failed", ex.getMessage()); }
        });
        MenuItem load = new MenuItem("Load…");
        load.setOnAction(e -> {
            try {
                CareHome loaded = CareHome.loadFromFile("carehome.bin");
                this.app = loaded;
                setStatus("Loaded carehome.bin");
                refreshBeds();
            } catch (RuntimeException ex) { showError("Load failed", ex.getMessage()); }
        });
        MenuItem exit = new MenuItem("Exit");
        exit.setAccelerator(KeyCombination.keyCombination("Ctrl+Q"));
        exit.setOnAction(e -> Platform.exit());
        file.getItems().addAll(save, load, new SeparatorMenuItem(), exit);

        Menu staff = new Menu("Staff");
        MenuItem addDoc = new MenuItem("Add Doctor");
        addDoc.setOnAction(e -> promptAddStaff(Role.DOCTOR));
        MenuItem addNurse = new MenuItem("Add Nurse");
        addNurse.setOnAction(e -> promptAddStaff(Role.NURSE));
        MenuItem setDocMins = new MenuItem("Set Doctor Minutes…");
        setDocMins.setOnAction(e -> promptSetDoctorMinutes());

        MenuItem listStaff = new MenuItem("List Staff (Console)");
        listStaff.setOnAction(e -> {
            try {
                app.printAllStaff();
                java.util.Map<Role, Long> counts = app.staffCounts();
                long mgr = counts.getOrDefault(Role.MANAGER, 0L);
                long doc = counts.getOrDefault(Role.DOCTOR, 0L);
                long nur = counts.getOrDefault(Role.NURSE, 0L);
                setStatus("Staff — Managers:" + mgr + " Doctors:" + doc + " Nurses:" + nur);
            } catch (Exception ex) { showError("List Staff", ex.getMessage()); }
        });

        MenuItem showRoster = new MenuItem("Show Nurse Roster (by ID)...");
        showRoster.setOnAction(e -> {
            TextInputDialog td = new TextInputDialog();
            td.setTitle("Nurse Roster");
            td.setHeaderText("Enter nurse ID:");
            td.showAndWait().ifPresent(nid -> {
                try {
                    String txt = app.nurseRosterSummary(nid);
                    showLongInfo("Roster — " + nid, txt);
                    setStatus("Roster shown for " + nid);
                } catch (Exception ex) { showError("Roster", ex.getMessage()); }
            });
        });

        MenuItem staffAudit = new MenuItem("Show Staff Audit…");
        staffAudit.setOnAction(e -> {
            TextInputDialog td = new TextInputDialog();
            td.setTitle("Staff Audit");
            td.setHeaderText("Enter staff ID:");
            td.showAndWait().ifPresent(id -> {
                try { app.printAuditForStaff(id); setStatus("Audit printed for " + id + " (console)"); }
                catch (Exception ex) { showError("Audit", ex.getMessage()); }
            });
        });

        MenuItem showStaff = new MenuItem("Show Staff…");
        showStaff.setOnAction(e -> showStaffList());

        staff.getItems().addAll(
                addDoc, addNurse, new SeparatorMenuItem(), setDocMins,
                new SeparatorMenuItem(), listStaff, showRoster, staffAudit,
                new SeparatorMenuItem(), showStaff
        );

        Menu schedule = new Menu("Schedule");
        MenuItem assignShift = new MenuItem("Assign Nurse Shift…");
        assignShift.setOnAction(e -> promptAssignShift());
        MenuItem removeShift = new MenuItem("Remove Nurse Shift…");
        removeShift.setOnAction(e -> promptRemoveShift());
        MenuItem checkCompliance = new MenuItem("Check Compliance");
        checkCompliance.setOnAction(e -> {
            try { svc.checkCompliance(); info("Compliance", "All good"); setStatus("Compliance OK"); }
            catch (Exception ex){ showError("Compliance failure", ex.getMessage()); }
        });
        MenuItem nurseWeek = new MenuItem("Nurse Roster — Whole Week");
        nurseWeek.setOnAction(e -> {
            String txt = app.nurseWeeklyRosterSummary();
            showLongInfo("Nurse Roster — Week", txt);
            setStatus("Nurse roster (week) shown");
        });
        MenuItem doctorWeek = new MenuItem("Doctor Coverage — Week");
        doctorWeek.setOnAction(e -> {
            String txt = app.doctorMinutesSummary();
            showLongInfo("Doctor Coverage — Week", txt);
            setStatus("Doctor coverage (week) shown");
        });
        MenuItem teamWeek = new MenuItem("Team Roster — Week");
        teamWeek.setOnAction(e -> {
            String txt = app.teamRosterSummary();
            showLongInfo("Team Roster — Week", txt);
            setStatus("Team roster (week) shown");
        });
        MenuItem rosterDashboard = new MenuItem("Roster Dashboard…");
        rosterDashboard.setOnAction(e -> showRosterDashboard());
        MenuItem exportRoster = new MenuItem("Export Week to roster_week.txt");
        exportRoster.setOnAction(e -> {
            try {
                String text = app.teamRosterSummary() + "\n\n" + app.allNursesRosterByPerson();
                java.nio.file.Files.write(java.nio.file.Paths.get("roster_week.txt"),
                        text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                info("Export", "Saved roster_week.txt in the app folder.");
                setStatus("Exported roster_week.txt");
            } catch (Exception ex) { showError("Export failed", ex.getMessage()); }
        });
        schedule.getItems().setAll(
                assignShift, removeShift, new SeparatorMenuItem(),
                nurseWeek, doctorWeek, teamWeek, rosterDashboard, exportRoster,
                new SeparatorMenuItem(), checkCompliance
        );

        Menu patients = new Menu("Patients");
        MenuItem admit = new MenuItem("Admit…"); admit.setOnAction(e -> promptAdmitPatient());
        MenuItem move = new MenuItem("Move…");  move.setOnAction(e -> promptMovePatient());
        MenuItem details = new MenuItem("Resident Details…"); details.setOnAction(e -> promptResidentDetails());
        MenuItem addRx = new MenuItem("Add Prescription…"); addRx.setOnAction(e -> promptAddPrescription());
        MenuItem adminMed = new MenuItem("Administer Medication…"); adminMed.setOnAction(e -> promptAdministerMedication());
        patients.getItems().addAll(admit, move, new SeparatorMenuItem(), details, addRx, adminMed);

        Menu audit = new Menu("Audit");
        MenuItem showAudit = new MenuItem("Show Audit in Console");
        showAudit.setOnAction(e -> app.printAudit());
        audit.getItems().add(showAudit);

        return new MenuBar(file, staff, schedule, patients, audit);
    }
    
    
    
    private void promptAdmitPatient(){
        Dialog<Boolean> d = new Dialog<>();
        d.setTitle("Admit Patient");
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField managerId = new TextField("m1");
        TextField pid = new TextField();
        TextField name = new TextField();
        ComboBox<Gender> gender = new ComboBox<>();
        gender.getItems().addAll(Gender.values());
        gender.getSelectionModel().select(Gender.F);
        CheckBox iso = new CheckBox("Requires isolation");

        ComboBox<String> ward = new ComboBox<>();
        ward.getItems().addAll("A","B"); ward.getSelectionModel().select("A");
        Spinner<Integer> room = new Spinner<>(1, 6, 1);
        Spinner<Integer> bed  = new Spinner<>(1, 4, 1);

        GridPane gp = formGrid(
                new Label("Manager ID (auth):"), managerId,
                new Label("Patient ID:"), pid,
                new Label("Full name:"), name,
                new Label("Gender:"), gender,
                new Label("Isolation:"), iso,
                new Label("Ward:"), ward,
                new Label("Room (1-6):"), room,
                new Label("Bed (1-4):"), bed
        );
        d.getDialogPane().setContent(gp);
        d.setResultConverter(btn -> btn==ButtonType.OK);
        d.showAndWait().ifPresent(ok -> {
            if (!ok) return;
            try {
                svc.admitPatient(
                        managerId.getText().trim(),
                        pid.getText().trim(),
                        name.getText().trim(),
                        gender.getValue(),
                        iso.isSelected(),
                        ward.getValue(),
                        room.getValue(),
                        bed.getValue()
                );
                setStatus("Admitted " + pid.getText().trim() + " to " + ward.getValue() + "-R" + room.getValue() + "-B" + bed.getValue());
                refreshBeds();
            } catch (Exception ex){ showError("Admit failed", ex.getMessage()); }
        });
    }

    private void promptMovePatient(){
        Dialog<Boolean> d = new Dialog<>();
        d.setTitle("Move Patient");
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField nurseId = new TextField();
        ComboBox<DayOfWeek> day = new ComboBox<>();
        day.getItems().addAll(DayOfWeek.values());
        day.getSelectionModel().select(DayOfWeek.MONDAY);
        Spinner<Integer> hour = new Spinner<>(0, 23, 10);

        ComboBox<String> fromWard = new ComboBox<>(); fromWard.getItems().addAll("A","B"); fromWard.getSelectionModel().select("A");
        Spinner<Integer> fromRoom = new Spinner<>(1, 6, 1);
        Spinner<Integer> fromBed  = new Spinner<>(1, 4, 1);

        ComboBox<String> toWard = new ComboBox<>(); toWard.getItems().addAll("A","B"); toWard.getSelectionModel().select("A");
        Spinner<Integer> toRoom = new Spinner<>(1, 6, 1);
        Spinner<Integer> toBed  = new Spinner<>(1, 4, 1);

        GridPane gp = formGrid(
                new Label("Nurse ID (auth):"), nurseId,
                new Label("Day:"), day,
                new Label("Hour:"), hour,
                new Label("From Ward:"), fromWard,
                new Label("From Room:"), fromRoom,
                new Label("From Bed:"), fromBed,
                new Label("To Ward:"), toWard,
                new Label("To Room:"), toRoom,
                new Label("To Bed:"), toBed
        );
        d.getDialogPane().setContent(gp);
        d.setResultConverter(btn -> btn==ButtonType.OK);
        d.showAndWait().ifPresent(ok -> {
            if (!ok) return;
            try {
                svc.movePatient(
                        nurseId.getText().trim(),
                        day.getValue(),
                        java.time.LocalTime.of(hour.getValue(), 0),
                        fromWard.getValue(), fromRoom.getValue(), fromBed.getValue(),
                        toWard.getValue(), toRoom.getValue(), toBed.getValue()
                );
                setStatus("Moved patient from " +
                        fromWard.getValue()+"-R"+fromRoom.getValue()+"-B"+fromBed.getValue() +
                        " to " +
                        toWard.getValue()+"-R"+toRoom.getValue()+"-B"+toBed.getValue());
                refreshBeds();
            } catch (Exception ex){ showError("Move failed", ex.getMessage()); }
        });
    }


    private void showLongInfo(String title, String msg){
        TextArea ta = new TextArea(msg);
        ta.setEditable(false);
        ta.setWrapText(true);
        ta.setPrefSize(720, 480);
        Dialog<Void> d = new Dialog<>();
        d.setTitle(title);
        d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        d.getDialogPane().setContent(ta);
        d.showAndWait();
    }

    private void promptResidentDetails(){
        Dialog<Boolean> d = new Dialog<>();
        d.setTitle("Resident Details");
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        ComboBox<String> ward = new ComboBox<>(); ward.getItems().addAll("A","B"); ward.getSelectionModel().select("A");
        Spinner<Integer> room = new Spinner<>(1, 6, 1);
        Spinner<Integer> bed  = new Spinner<>(1, 4, 1);
        GridPane gp = formGrid(new Label("Ward:"), ward, new Label("Room (1-6):"), room, new Label("Bed (1-4):"), bed);
        d.getDialogPane().setContent(gp);
        d.setResultConverter(btn -> btn==ButtonType.OK);
        d.showAndWait().ifPresent(ok -> {
            if (!ok) return;
            try {
                String pid = svc.patientIdInBed(ward.getValue(), room.getValue(), bed.getValue());
                if (pid == null){ info("Resident Details", "Bed is vacant."); return; }

                java.util.List<Prescription> rxs = svc.loadPrescriptionsForPatient(pid);
                java.util.List<MedicationAdministration> admins = svc.administrationsForPatient(pid);

                String rxText = rxs.isEmpty() ? "(no prescriptions)" :
                        rxs.stream().map(Prescription::toString).reduce((a,b)->a+"\n"+b).get();
                String adText = admins.isEmpty() ? "(no administrations)" :
                        admins.stream().map(Object::toString).reduce((a,b)->a+"\n"+b).get();

                String msg = "Patient ID: " + pid + "\n"
                           + ward.getValue()+"-R"+room.getValue()+"-B"+bed.getValue() + "\n\n"
                           + "Prescriptions:\n" + rxText + "\n\n"
                           + "Administrations:\n" + adText;
                showLongInfo("Resident Details", msg);
            } catch (Exception ex){ showError("Resident Details", ex.getMessage()); }
        });
    }

    private void promptAddPrescription(){
        Dialog<Boolean> d = new Dialog<>();
        d.setTitle("Add Prescription");
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        TextField doctorId = new TextField();
        ComboBox<DayOfWeek> day = new ComboBox<>(); day.getItems().addAll(DayOfWeek.values()); day.getSelectionModel().select(DayOfWeek.MONDAY);
        ComboBox<String> ward = new ComboBox<>(); ward.getItems().addAll("A","B"); ward.getSelectionModel().select("A");
        Spinner<Integer> room = new Spinner<>(1, 6, 1);
        Spinner<Integer> bed  = new Spinner<>(1, 4, 1);
        TextField med = new TextField();
        TextField dose = new TextField();
        TextField times = new TextField("08:00, 14:00");
        GridPane gp = formGrid(
                new Label("Doctor ID (auth):"), doctorId,
                new Label("Day:"), day,
                new Label("Ward:"), ward,
                new Label("Room (1-6):"), room,
                new Label("Bed (1-4):"), bed,
                new Label("Medicine:"), med,
                new Label("Dose:"), dose,
                new Label("Times:"), times
        );
        d.getDialogPane().setContent(gp);
        d.setResultConverter(btn -> btn==ButtonType.OK);
        d.showAndWait().ifPresent(ok -> {
            if (!ok) return;
            try {
                String pid = svc.patientIdInBed(ward.getValue(), room.getValue(), bed.getValue());
                if (pid == null) { showError("Add Prescription", "Chosen bed is vacant"); return; }
                if (!doctorExists(doctorId.getText())) { showError("Add Prescription", "Doctor '"+doctorId.getText()+"' does not exist."); return; }
                svc.doctorAddPrescription(
                        doctorId.getText().trim(), pid, day.getValue(),
                        med.getText().trim(), dose.getText().trim(), times.getText().trim()
                );
                setStatus("RX added for " + pid);
            } catch (Exception ex){ showError("Add Prescription", ex.getMessage()); }
        });
    }

    private void promptAdministerMedication(){
        Dialog<Boolean> d = new Dialog<>();
        d.setTitle("Administer Medication");
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        TextField nurseId = new TextField();
        ComboBox<DayOfWeek> day = new ComboBox<>(); day.getItems().addAll(DayOfWeek.values()); day.getSelectionModel().select(DayOfWeek.MONDAY);
        Spinner<Integer> hour = new Spinner<>(0,23,10);
        ComboBox<String> ward = new ComboBox<>(); ward.getItems().addAll("A","B"); ward.getSelectionModel().select("A");
        Spinner<Integer> room = new Spinner<>(1, 6, 1);
        Spinner<Integer> bed  = new Spinner<>(1, 4, 1);
        TextField med = new TextField();
        TextField dose = new TextField();
        GridPane gp = formGrid(
                new Label("Nurse ID (auth):"), nurseId,
                new Label("Day:"), day,
                new Label("Hour:"), hour,
                new Label("Ward:"), ward,
                new Label("Room (1-6):"), room,
                new Label("Bed (1-4):"), bed,
                new Label("Medicine:"), med,
                new Label("Dose:"), dose
        );
        d.getDialogPane().setContent(gp);
        d.setResultConverter(btn -> btn==ButtonType.OK);
        d.showAndWait().ifPresent(ok -> {
            if (!ok) return;
            try {
                String pid = svc.patientIdInBed(ward.getValue(), room.getValue(), bed.getValue());
                if (pid == null) { showError("Administer", "Chosen bed is vacant"); return; }
                if (!nurseExists(nurseId.getText())) { showError("Administer Medication", "Nurse '"+nurseId.getText()+"' does not exist."); return; }
                svc.administerMedication(
                        nurseId.getText().trim(), day.getValue(), LocalTime.of(hour.getValue(), 0),
                        pid, med.getText().trim(), dose.getText().trim()
                );
                setStatus("Medication administered");
                refreshBeds();
            } catch (Exception ex){ showError("Administer Medication", ex.getMessage()); }
        });
    }

    private void promptAddStaff(Role role){
        Dialog<Triple<String,String,String>> d = new Dialog<>();
        d.setTitle("Add " + role);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        TextField managerId = new TextField("m1");
        TextField id = new TextField();
        TextField name = new TextField();
        GridPane gp = formGrid(
                new Label("Manager ID (auth):"), managerId,
                new Label(role == Role.DOCTOR ? "Doctor ID:" : "Nurse ID:"), id,
                new Label("Name:"), name);
        d.getDialogPane().setContent(gp);
        d.setResultConverter(btn -> btn==ButtonType.OK ? new Triple<>(managerId.getText(), id.getText(), name.getText()) : null);
        d.showAndWait().ifPresent(t -> {
            try {
                if (role==Role.DOCTOR) svc.addDoctor(t.b, t.c);
                else svc.addNurse(t.b, t.c);
                setStatus(role + " added: " + t.b);
            } catch (Exception ex){ showError("Add " + role + " failed", ex.getMessage()); }
        });
    }

    private void promptSetDoctorMinutes(){
        Dialog<Triple<String,DayOfWeek,Integer>> d = new Dialog<>();
        d.setTitle("Set Doctor Minutes");
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        TextField managerId = new TextField("m1");
        ComboBox<DayOfWeek> day = new ComboBox<>(); day.getItems().addAll(DayOfWeek.values()); day.getSelectionModel().select(DayOfWeek.MONDAY);
        TextField mins = new TextField("60");
        GridPane gp = formGrid(new Label("Manager ID (auth):"), managerId, new Label("Day:"), day, new Label("Minutes (≥60):"), mins);
        d.getDialogPane().setContent(gp);
        d.setResultConverter(btn -> {
            if (btn!=ButtonType.OK) return null;
            try { return new Triple<>(managerId.getText(), day.getValue(), Integer.parseInt(mins.getText().trim())); }
            catch (NumberFormatException nfe){ showError("Invalid minutes", "Please enter a number"); return null; }
        });
        d.showAndWait().ifPresent(t -> {
            try { svc.setDoctorMinutes(t.a, t.b, t.c); setStatus("Doctor minutes set for " + t.b + " = " + t.c); }
            catch (Exception ex){ showError("Set minutes failed", ex.getMessage()); }
        });
    }

    private void promptAssignShift(){
        Dialog<Quad<String,String,DayOfWeek,Boolean>> d = new Dialog<>();
        d.setTitle("Assign Nurse Shift");
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        TextField managerId = new TextField("m1");
        TextField nurseId = new TextField();
        ComboBox<DayOfWeek> day = new ComboBox<>(); day.getItems().addAll(DayOfWeek.values()); day.getSelectionModel().select(DayOfWeek.MONDAY);
        ComboBox<String> shift = new ComboBox<>(); shift.getItems().addAll("A (08-16)", "B (14-22)"); shift.getSelectionModel().select(0);
        GridPane gp = formGrid(new Label("Manager ID (auth):"), managerId, new Label("Nurse ID:"), nurseId, new Label("Day:"), day, new Label("Shift:"), shift);
        d.getDialogPane().setContent(gp);
        d.setResultConverter(btn -> btn==ButtonType.OK ? new Quad<>(managerId.getText(), nurseId.getText(), day.getValue(), shift.getSelectionModel().getSelectedIndex()==0) : null);
        d.showAndWait().ifPresent(t -> {
            try {
                if (!nurseExists(t.b)) { showError("Assign shift failed", "Nurse '"+t.b+"' does not exist."); return; }
                svc.assignNurseShift(t.a, t.b, t.c, t.d);
                setStatus("Shift assigned");
            } catch (Exception ex){ showError("Assign shift failed", ex.getMessage()); }
        });
    }

    private void promptRemoveShift(){
        Dialog<Quad<String,String,DayOfWeek,Boolean>> d = new Dialog<>();
        d.setTitle("Remove Nurse Shift");
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        TextField managerId = new TextField("m1");
        TextField nurseId = new TextField();
        ComboBox<DayOfWeek> day = new ComboBox<>(); day.getItems().addAll(DayOfWeek.values()); day.getSelectionModel().select(DayOfWeek.MONDAY);
        ComboBox<String> shift = new ComboBox<>(); shift.getItems().addAll("A (08-16)", "B (14-22)"); shift.getSelectionModel().select(0);
        GridPane gp = formGrid(new Label("Manager ID (auth):"), managerId, new Label("Nurse ID:"), nurseId, new Label("Day:"), day, new Label("Shift:"), shift);
        d.getDialogPane().setContent(gp);
        d.setResultConverter(btn -> btn==ButtonType.OK ? new Quad<>(managerId.getText(), nurseId.getText(), day.getValue(), shift.getSelectionModel().getSelectedIndex()==0) : null);
        d.showAndWait().ifPresent(t -> {
            try { svc.removeNurseShift(t.a, t.b, t.c, t.d); setStatus("Shift removed"); }
            catch (Exception ex){ showError("Remove shift failed", ex.getMessage()); }
        });
    }

    private Node buildBedPane(){
        bedGrid.setPadding(new Insets(12));
        bedGrid.setHgap(10);
        bedGrid.setVgap(10);
        bedGrid.setPrefColumns(8);
        ScrollPane scroller = new ScrollPane(bedGrid);
        scroller.setFitToWidth(true);
        scroller.setFitToHeight(true);
        return scroller;
    }

    private HBox buildStatusBar(){
        HBox hb = new HBox(12, status);
        hb.setPadding(new Insets(6,10,6,10));
        hb.setAlignment(Pos.CENTER_LEFT);
        hb.setStyle("-fx-background-color: -fx-control-inner-background; -fx-border-color: #ddd;");
        return hb;
    }

    private void refreshBeds(){
        bedGrid.getChildren().clear();
        java.util.List<BedCell> cells = loadBedCellsFromDB();
        for (int i = 0; i < cells.size(); i++){
            final BedCell bc = cells.get(i);
            Button btn = new Button(bc.cellLabel());
            btn.setPrefSize(120, 56);
            styleBedButtonDB(btn, bc);
            btn.setOnAction(e -> onBedClickedDB(bc));
            bedGrid.getChildren().add(btn);
        }
        setStatus("Beds: " + cells.size());
    }
    
    private boolean nurseExists(String id){
        String sql = "SELECT 1 FROM staff WHERE id=? AND role='NURSE' LIMIT 1";
        try (java.sql.Connection c = rmit.s4134401.carehome.util.DB.get();
             java.sql.PreparedStatement ps = c.prepareStatement(sql)){
            ps.setString(1, id.trim());
            try (java.sql.ResultSet rs = ps.executeQuery()){ return rs.next(); }
        } catch (Exception e){ showError("DB Error", e.getMessage()); return false; }
    }

    private boolean doctorExists(String id){
        String sql = "SELECT 1 FROM staff WHERE id=? AND role='DOCTOR' LIMIT 1";
        try (java.sql.Connection c = rmit.s4134401.carehome.util.DB.get();
             java.sql.PreparedStatement ps = c.prepareStatement(sql)){
            ps.setString(1, id.trim());
            try (java.sql.ResultSet rs = ps.executeQuery()){ return rs.next(); }
        } catch (Exception e){ showError("DB Error", e.getMessage()); return false; }
    }

    private java.util.List<String> loadStaffFromDB(){
        java.util.List<String> rows = new java.util.ArrayList<>();
        String sql = "SELECT id, name, role FROM staff ORDER BY role, id";
        try (java.sql.Connection c = rmit.s4134401.carehome.util.DB.get();
             java.sql.Statement st = c.createStatement();
             java.sql.ResultSet rs = st.executeQuery(sql)){
            while (rs.next()){
                rows.add(String.format("%-6s  %-6s  %s",
                        rs.getString("role"), rs.getString("id"), rs.getString("name")));
            }
        } catch (Exception e){ showError("DB Error", e.getMessage()); }
        return rows;
    }

    private void showStaffList(){
        java.util.List<String> lines = loadStaffFromDB();
        String body = lines.isEmpty() ? "(no staff)" : String.join("\n", lines);
        showLongInfo("Staff (role  id  name)", body);
    }


    private void onBedClickedDB(BedCell b){
        if (b.isVacant()) info("Bed", b.ward + "-R" + b.room + "-B" + b.bedNum + "\n[vacant]");
        else info("Resident", b.patientId + " (" + b.gender + (b.isolation? ", isolation":"") + ")\n" +
                            b.ward + "-R" + b.room + "-B" + b.bedNum);
    }

    private void styleBedButtonDB(Button btn, BedCell b){
        if (b.isVacant()){
            btn.setStyle("-fx-background-color: linear-gradient(#e9ffe9,#c6f3c6); -fx-border-color:#7ecb7e; -fx-border-radius:8; -fx-background-radius:8;");
            return;
        }
        String baseColor = (b.gender==Gender.M)
                ? "-fx-background-color: linear-gradient(#e6f0ff,#cfe0ff);"
                : "-fx-background-color: linear-gradient(#ffe6f0,#ffd0df);";
        String border = b.isolation
                ? "-fx-border-color: #f39c12; -fx-border-width:2;"
                : "-fx-border-color: #b3b3b3;";
        btn.setStyle(baseColor + " -fx-background-radius:8; -fx-border-radius:8; " + border);
        btn.setTextFill(javafx.scene.paint.Color.web("#222"));
    }

    private GridPane formGrid(javafx.scene.Node... nodes){
        GridPane gp = new GridPane();
        gp.setHgap(10); gp.setVgap(10); gp.setPadding(new Insets(12));
        for (int i=0;i<nodes.length;i+=2){
            gp.add(nodes[i], 0, i/2);
            if (i+1<nodes.length) gp.add(nodes[i+1], 1, i/2);
        }
        ColumnConstraints c0 = new ColumnConstraints(); c0.setPercentWidth(40);
        ColumnConstraints c1 = new ColumnConstraints(); c1.setPercentWidth(60);
        gp.getColumnConstraints().addAll(c0,c1);
        return gp;
    }

    private void showError(String title, String msg){
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setTitle(title); a.showAndWait(); setStatus("Error: " + msg);
    }
    private void info(String title, String msg){
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.setTitle(title); a.showAndWait(); setStatus(msg);
    }
    private void setStatus(String s){ status.setText(s); }

    private static final class Triple<A,B,C>{ final A a; final B b; final C c; Triple(A a,B b,C c){this.a=a;this.b=b;this.c=c;} }
    private static final class Quad<A,B,C,D>{ final A a; final B b; final C c; final D d; Quad(A a,B b,C c,D d){this.a=a;this.b=b;this.c=c;this.d=d;} }

    public static void main(String[] args) {
        launch(args);
    }
}
