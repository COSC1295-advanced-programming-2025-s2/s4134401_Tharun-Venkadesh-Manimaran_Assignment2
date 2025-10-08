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
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Optional;

public class CareHomeApp extends Application {

    private CareHome app = new CareHome();
    private final TilePane bedGrid = new TilePane();
    private final Text status = new Text("Ready");

    @Override
    public void start(Stage stage) {
        app.addManager("m1","Manager One");

        BorderPane root = new BorderPane();
        root.setTop(buildMenuBar(stage));
        root.setCenter(buildBedPane());
        root.setBottom(buildStatusBar());

        Scene scene = new Scene(root, 1000, 650);
        stage.setTitle("Resident HealthCare System — S2 2025");
        stage.setScene(scene);
        stage.show();

        refreshBeds();
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
            } catch (RuntimeException ex) {
                showError("Load failed", ex.getMessage());
            }
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

        staff.getItems().addAll(
                addDoc, addNurse, new SeparatorMenuItem(), setDocMins,
                new SeparatorMenuItem(), listStaff, showRoster, staffAudit
        );

        Menu schedule = new Menu("Schedule");
        MenuItem assignShift = new MenuItem("Assign Nurse Shift…");
        assignShift.setOnAction(e -> promptAssignShift());
        MenuItem removeShift = new MenuItem("Remove Nurse Shift…");
        removeShift.setOnAction(e -> promptRemoveShift());
        MenuItem checkCompliance = new MenuItem("Check Compliance");
        checkCompliance.setOnAction(e -> {
            try { app.checkCompliance(); info("Compliance", "All good ✅"); setStatus("Compliance OK"); }
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
            } catch (Exception ex) {
                showError("Export failed", ex.getMessage());
            }
        });

        schedule.getItems().setAll(
                assignShift, removeShift, new SeparatorMenuItem(),
                nurseWeek, doctorWeek, teamWeek, rosterDashboard, exportRoster,
                new SeparatorMenuItem(), checkCompliance
        );

        Menu patients = new Menu("Patients");
        MenuItem admit = new MenuItem("Admit…");
        admit.setOnAction(e -> promptAdmitPatient());
        MenuItem move = new MenuItem("Move…");
        move.setOnAction(e -> promptMovePatient());

        MenuItem details = new MenuItem("Resident Details…");
        details.setOnAction(e -> promptResidentDetails());
        MenuItem addRx = new MenuItem("Add Prescription…");
        addRx.setOnAction(e -> promptAddPrescription());
        MenuItem adminMed = new MenuItem("Administer Medication…");
        adminMed.setOnAction(e -> promptAdministerMedication());

        patients.getItems().addAll(admit, move, new SeparatorMenuItem(), details, addRx, adminMed);

        Menu audit = new Menu("Audit");
        MenuItem showAudit = new MenuItem("Show Audit in Console");
        showAudit.setOnAction(e -> app.printAudit());
        audit.getItems().add(showAudit);

        return new MenuBar(file, staff, schedule, patients, audit);
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
    	if (!ensureBedsAvailable("This action")) return;
        Dialog<Integer> d = new Dialog<Integer>();
        d.setTitle("Resident Details");
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Spinner<Integer> bedIdx = new Spinner<Integer>(0, Math.max(0, app.beds.size()-1), 0);
        GridPane gp = formGrid(new Label("Bed index:"), bedIdx);
        d.getDialogPane().setContent(gp);
        d.setResultConverter(btn -> btn==ButtonType.OK ? bedIdx.getValue() : null);
        d.showAndWait().ifPresent(idx -> {
            try{
                Bed b = app.beds.get(idx);
                if (b.isVacant()){ info("Resident Details", "Bed is vacant."); return; }
                Patient p = b.getOccupant();
                String msg = p.toString()
                        + "\n\nPrescriptions:\n" + app.prescriptionsSummary(p.id())
                        + "\n\nAdministrations:\n" + app.administrationsSummary(p.id());
                info("Resident Details", msg);
            } catch (Exception ex){ showError("Resident Details", ex.getMessage()); }
        });
    }

    private void promptAddPrescription(){
    	if (!ensureBedsAvailable("This action")) return;
        Dialog<Boolean> d = new Dialog<Boolean>();
        d.setTitle("Add Prescription");
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        TextField doctorId = new TextField();
        ComboBox<DayOfWeek> day = new ComboBox<DayOfWeek>();
        day.getItems().addAll(DayOfWeek.values()); day.getSelectionModel().select(DayOfWeek.MONDAY);
        Spinner<Integer> bedIndex = new Spinner<Integer>(0, Math.max(0, app.beds.size()-1), 0);
        TextField med = new TextField();
        TextField dose = new TextField();
        TextField times = new TextField("08:00, 14:00");
        GridPane gp = formGrid(
                new Label("Doctor ID (auth):"), doctorId,
                new Label("Day:"), day,
                new Label("Bed index (occupied):"), bedIndex,
                new Label("Medicine:"), med,
                new Label("Dose:"), dose,
                new Label("Times:"), times
        );
        d.getDialogPane().setContent(gp);
        d.setResultConverter(btn -> btn==ButtonType.OK);
        d.showAndWait().ifPresent(ok -> {
            if (!ok) return;
            try{
                Bed b = app.beds.get(bedIndex.getValue());
                if (b.isVacant()) { showError("Add Prescription", "Chosen bed is vacant"); return; }
                Patient p = b.getOccupant();
                Prescription rx = new Prescription(p.id());
                rx.addLine(med.getText().trim(), dose.getText().trim(), times.getText().trim());
                app.doctorAddPrescription(doctorId.getText().trim(), b, day.getValue(), rx);
                setStatus("RX added for " + p.id());
            } catch (Exception ex){ showError("Add Prescription", ex.getMessage()); }
        });
    }

    private void promptAdministerMedication(){
    	if (!ensureBedsAvailable("This action")) return;
        Dialog<Boolean> d = new Dialog<Boolean>();
        d.setTitle("Administer Medication");
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        TextField nurseId = new TextField();
        ComboBox<DayOfWeek> day = new ComboBox<DayOfWeek>();
        day.getItems().addAll(DayOfWeek.values()); day.getSelectionModel().select(DayOfWeek.MONDAY);
        Spinner<Integer> hour = new Spinner<Integer>(0,23,10);
        Spinner<Integer> bedIndex = new Spinner<Integer>(0, Math.max(0, app.beds.size()-1), 0);
        TextField med = new TextField();
        TextField dose = new TextField();
        GridPane gp = formGrid(
                new Label("Nurse ID (auth):"), nurseId,
                new Label("Day:"), day,
                new Label("Hour:"), hour,
                new Label("Bed index (occupied):"), bedIndex,
                new Label("Medicine:"), med,
                new Label("Dose:"), dose
        );
        d.getDialogPane().setContent(gp);
        d.setResultConverter(btn -> btn==ButtonType.OK);
        d.showAndWait().ifPresent(ok -> {
            if (!ok) return;
            try{
                Bed b = app.beds.get(bedIndex.getValue());
                if (b.isVacant()) { showError("Administer", "Chosen bed is vacant"); return; }
                app.administerMedication(
                        nurseId.getText().trim(), b, day.getValue(),
                        java.time.LocalTime.of(hour.getValue(),0),
                        med.getText().trim(), dose.getText().trim()
                );
                setStatus("Medication administered");
                refreshBeds();
            } catch (Exception ex){ showError("Administer Medication", ex.getMessage()); }
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
        for (int i = 0; i < app.beds.size(); i++){
            Bed b = app.beds.get(i);
            Button btn = new Button(cellLabel(b));
            btn.setPrefSize(120, 56);
            styleBedButton(btn, b);
            final Bed bedRef = b;
            btn.setOnAction(e -> onBedClicked(bedRef));
            bedGrid.getChildren().add(btn);
        }
        setStatus("Beds: " + app.beds.size());
    }

    private void onBedClicked(Bed b){
        if (b.isVacant()) info("Bed", b.toString());
        else info("Resident", b.getOccupant().toString());
    }

    private String cellLabel(Bed b){
        String base = b.ward() + "-R" + b.room() + "-B" + b.bedNum();
        if (b.isVacant()) return base + "\n[vacant]";
        return base + "\n" + b.getOccupant().id();
    }

    private void styleBedButton(Button btn, Bed b){
        if (b.isVacant()){
            btn.setStyle("-fx-background-color: linear-gradient(#e9ffe9,#c6f3c6); -fx-border-color:#7ecb7e; -fx-border-radius:8; -fx-background-radius:8;");
            return;
        }
        Patient p = b.getOccupant();
        String baseColor = (p.gender()==Gender.M)
                ? "-fx-background-color: linear-gradient(#e6f0ff,#cfe0ff);"
                : "-fx-background-color: linear-gradient(#ffe6f0,#ffd0df);";
        String border = p.needsIsolation()
                ? "-fx-border-color: #f39c12; -fx-border-width:2;"
                : "-fx-border-color: #b3b3b3;";
        btn.setStyle(baseColor + " -fx-background-radius:8; -fx-border-radius:8; " + border);
        btn.setTextFill(Color.web("#222"));
    }


    private void promptAddStaff(Role role){
        Dialog<Triple<String,String,String>> d = new Dialog<Triple<String,String,String>>();
        d.setTitle("Add " + role);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        TextField id = new TextField();
        TextField name = new TextField();
        TextField managerId = new TextField("m1");
        GridPane gp = formGrid(
                new Label("Manager ID (auth):"), managerId,
                new Label(role == Role.DOCTOR ? "Doctor ID:" : "Nurse ID:"), id,
                new Label("Name:"), name);
        d.getDialogPane().setContent(gp);
        d.setResultConverter(btn -> btn==ButtonType.OK ? new Triple<String,String,String>(managerId.getText(), id.getText(), name.getText()) : null);
        Optional<Triple<String,String,String>> res = d.showAndWait();
        if (res.isPresent()){
            Triple<String,String,String> t = res.get();
            try{
                if (role==Role.DOCTOR) app.addDoctor(t.b, t.c);
                else app.addNurse(t.b, t.c);
                setStatus(role + " added: " + t.b);
                refreshBeds();
            } catch (Exception ex){ showError("Add " + role + " failed", ex.getMessage()); }
        }
    }

    private void promptSetDoctorMinutes(){
        Dialog<Triple<String,DayOfWeek,Integer>> d = new Dialog<Triple<String,DayOfWeek,Integer>>();
        d.setTitle("Set Doctor Minutes");
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        TextField managerId = new TextField("m1");
        ComboBox<DayOfWeek> day = new ComboBox<DayOfWeek>();
        day.getItems().addAll(DayOfWeek.values());
        day.getSelectionModel().select(DayOfWeek.MONDAY);
        TextField mins = new TextField("60");
        GridPane gp = formGrid(
                new Label("Manager ID (auth):"), managerId,
                new Label("Day:"), day,
                new Label("Minutes (≥60):"), mins);
        d.getDialogPane().setContent(gp);
        d.setResultConverter(btn -> {
            if (btn!=ButtonType.OK) return null;
            try { return new Triple<String,DayOfWeek,Integer>(managerId.getText(), day.getValue(), Integer.valueOf(Integer.parseInt(mins.getText().trim()))); }
            catch (NumberFormatException nfe){ showError("Invalid minutes", "Please enter a number"); return null; }
        });
        Optional<Triple<String,DayOfWeek,Integer>> res = d.showAndWait();
        if (res.isPresent()){
            Triple<String,DayOfWeek,Integer> t = res.get();
            try { app.setDoctorMinutes(t.a, t.b, t.c.intValue()); setStatus("Doctor minutes set for " + t.b + " = " + t.c); }
            catch (Exception ex){ showError("Set minutes failed", ex.getMessage()); }
        }
    }

    private void promptAssignShift(){
        Dialog<Quad<String,String,DayOfWeek,Boolean>> d = new Dialog<Quad<String,String,DayOfWeek,Boolean>>();
        d.setTitle("Assign Nurse Shift");
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        TextField managerId = new TextField("m1");
        TextField nurseId = new TextField();
        ComboBox<DayOfWeek> day = new ComboBox<DayOfWeek>();
        day.getItems().addAll(DayOfWeek.values());
        day.getSelectionModel().select(DayOfWeek.MONDAY);
        ComboBox<String> shift = new ComboBox<String>();
        shift.getItems().addAll("A (08-16)", "B (14-22)");
        shift.getSelectionModel().select(0);
        GridPane gp = formGrid(
                new Label("Manager ID (auth):"), managerId,
                new Label("Nurse ID:"), nurseId,
                new Label("Day:"), day,
                new Label("Shift:"), shift);
        d.getDialogPane().setContent(gp);
        d.setResultConverter(btn -> {
            if (btn!=ButtonType.OK) return null;
            return new Quad<String,String,DayOfWeek,Boolean>(managerId.getText(), nurseId.getText(), day.getValue(), Boolean.valueOf(shift.getSelectionModel().getSelectedIndex()==0));
        });
        Optional<Quad<String,String,DayOfWeek,Boolean>> res = d.showAndWait();
        if (res.isPresent()){
            Quad<String,String,DayOfWeek,Boolean> t = res.get();
            try { app.assignNurseShift(t.a, t.b, t.c, t.d.booleanValue()); setStatus("Shift assigned"); }
            catch (Exception ex){ showError("Assign shift failed", ex.getMessage()); }
        }
    }

    private void promptRemoveShift(){
        Dialog<Quad<String,String,DayOfWeek,Boolean>> d = new Dialog<Quad<String,String,DayOfWeek,Boolean>>();
        d.setTitle("Remove Nurse Shift");
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        TextField managerId = new TextField("m1");
        TextField nurseId = new TextField();
        ComboBox<DayOfWeek> day = new ComboBox<DayOfWeek>();
        day.getItems().addAll(DayOfWeek.values());
        day.getSelectionModel().select(DayOfWeek.MONDAY);
        ComboBox<String> shift = new ComboBox<String>();
        shift.getItems().addAll("A (08-16)", "B (14-22)");
        shift.getSelectionModel().select(0);
        GridPane gp = formGrid(
                new Label("Manager ID (auth):"), managerId,
                new Label("Nurse ID:"), nurseId,
                new Label("Day:"), day,
                new Label("Shift:"), shift);
        d.getDialogPane().setContent(gp);
        d.setResultConverter(btn -> {
            if (btn!=ButtonType.OK) return null;
            return new Quad<String,String,DayOfWeek,Boolean>(managerId.getText(), nurseId.getText(), day.getValue(), Boolean.valueOf(shift.getSelectionModel().getSelectedIndex()==0));
        });
        Optional<Quad<String,String,DayOfWeek,Boolean>> res = d.showAndWait();
        if (res.isPresent()){
            Quad<String,String,DayOfWeek,Boolean> t = res.get();
            try { app.removeNurseShift(t.a, t.b, t.c, t.d.booleanValue()); setStatus("Shift removed"); }
            catch (Exception ex){ showError("Remove shift failed", ex.getMessage()); }
        }
    }

    private void promptAdmitPatient(){
    	if (!ensureBedsAvailable("This action")) return;
        Dialog<AdmitArgs> d = new Dialog<AdmitArgs>();
        d.setTitle("Admit Patient");
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        TextField managerId = new TextField("m1");
        TextField pid = new TextField();
        TextField name = new TextField();
        ComboBox<Gender> gender = new ComboBox<Gender>();
        gender.getItems().addAll(Gender.values());
        gender.getSelectionModel().select(Gender.F);
        CheckBox iso = new CheckBox("Requires isolation");
        Spinner<Integer> bedIndex = new Spinner<Integer>(0, Math.max(0, app.beds.size()-1), 0);
        GridPane gp = formGrid(
                new Label("Manager ID (auth):"), managerId,
                new Label("Patient ID:"), pid,
                new Label("Full name:"), name,
                new Label("Gender:"), gender,
                new Label("Bed index:"), bedIndex,
                new Label(""), iso
        );
        d.getDialogPane().setContent(gp);
        d.setResultConverter(btn -> {
            if (btn!=ButtonType.OK) return null;
            return new AdmitArgs(managerId.getText(), pid.getText(), name.getText(),
                    gender.getValue(), iso.isSelected(), bedIndex.getValue());
        });
        Optional<AdmitArgs> res = d.showAndWait();
        if (res.isPresent()){
            AdmitArgs a = res.get();
            try{
                Bed target = app.beds.get(a.bedIdx);
                if (!target.isVacant()) {
                    showError("Admit failed", "Bed is already occupied.");
                    return;
                }
                app.admitPatient(a.managerId, new Patient(a.pid, a.name, a.gender, a.iso), target);
                setStatus("Admitted " + a.pid + " to " + target);
                refreshBeds();
            } catch (Exception ex){ showError("Admit failed", ex.getMessage()); }
        }
    }

    private void promptMovePatient(){
    	if (!ensureBedsAvailable("This action")) return;
        Dialog<MoveArgs> d = new Dialog<MoveArgs>();
        d.setTitle("Move Patient");
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        TextField nurseId = new TextField();
        ComboBox<DayOfWeek> day = new ComboBox<DayOfWeek>();
        day.getItems().addAll(DayOfWeek.values());
        day.getSelectionModel().select(DayOfWeek.MONDAY);
        Spinner<Integer> fromIdx = new Spinner<Integer>(0, Math.max(0, app.beds.size()-1), 0);
        Spinner<Integer> toIdx   = new Spinner<Integer>(0, Math.max(0, app.beds.size()-1), 1);
        Spinner<Integer> hour    = new Spinner<Integer>(0, 23, 10);
        GridPane gp = formGrid(
                new Label("Nurse ID (auth):"), nurseId,
                new Label("Day:"), day,
                new Label("Hour:"), hour,
                new Label("From bed index:"), fromIdx,
                new Label("To bed index:"), toIdx
        );
        d.getDialogPane().setContent(gp);
        d.setResultConverter(btn -> {
            if (btn!=ButtonType.OK) return null;
            return new MoveArgs(nurseId.getText(), day.getValue(), hour.getValue(),
                    fromIdx.getValue(), toIdx.getValue());
        });
        Optional<MoveArgs> res = d.showAndWait();
        if (res.isPresent()){
            MoveArgs a = res.get();
            try{
                Bed from = app.beds.get(a.fromIdx);
                Bed to   = app.beds.get(a.toIdx);
                app.movePatient(a.nurseId, from, to, a.day, LocalTime.of(a.hour,0));
                setStatus("Moved patient from " + from + " to " + to);
                refreshBeds();
            } catch (Exception ex){ showError("Move failed", ex.getMessage()); }
        }
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

    private static final class AdmitArgs {
        final String managerId, pid, name; final Gender gender; final boolean iso; final int bedIdx;
        AdmitArgs(String managerId, String pid, String name, Gender gender, boolean iso, int bedIdx){
            this.managerId=managerId; this.pid=pid; this.name=name; this.gender=gender; this.iso=iso; this.bedIdx=bedIdx;
        }
    }
    private static final class MoveArgs {
        final String nurseId; final DayOfWeek day; final int hour; final int fromIdx; final int toIdx;
        MoveArgs(String nurseId, DayOfWeek day, int hour, int fromIdx, int toIdx){
            this.nurseId=nurseId; this.day=day; this.hour=hour; this.fromIdx=fromIdx; this.toIdx=toIdx;
        }
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
    
    private boolean ensureBedsAvailable(String action){
        if (app.beds.isEmpty()){
            showError(action, "No beds available yet.");
            return false;
        }
        return true;
    }


    public static void main(String[] args) {
        launch(args);
    }

}
