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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import rmit.s4134401.carehome.service.CareHomeService;
import rmit.s4134401.carehome.repo.jdbc.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;

public class CareHomeApp extends Application {

	private String currentUserId = null;
	private Role currentUserRole = null;

	private static final String DEFAULT_MANAGER_ID = "m1";
	private static final String DEFAULT_MANAGER_NAME = "Manager One";
	private static final String DEFAULT_MANAGER_BOOTSTRAP_PASSWORD = "m1";

	private MenuItem miSetDocMins, miListStaff, miShowRoster, miStaffAudit, miShowStaff;
	private MenuItem miAssignShift, miRemoveShift, miCheckCompliance, miNurseWeek, miDoctorWeek, miTeamWeek,
			miRosterDashboard, miExportRoster;
	private MenuItem miAdmit, miMove, miDetails, miAddRx, miAdminMed;
	private MenuItem miDischarge;
	private MenuItem miSwitchUser;
	private MenuItem miChangePw, miResetPw;
	private MenuItem miUpdateResident;
	private MenuItem miAddStaff;
	private MenuItem miModifyStaff;
	private MenuItem miRemoveDocMins;

	private final HBox wardsRoot = new HBox(16);

	private final Label userBadge = new Label("Not signed in");

	private CareHomeService svc;
	private CareHome app = new CareHome();

	private final Text status = new Text("Ready");

	private void ensureDemoAccounts() {
		try (java.sql.Connection c = rmit.s4134401.carehome.util.DB.get()) {
			try (java.sql.PreparedStatement ps = c.prepareStatement("INSERT INTO staff(id,name,role,password) "
					+ "SELECT 'm1', ?, 'MANAGER', 'm1' " + "WHERE NOT EXISTS (SELECT 1 FROM staff WHERE id='m1')")) {
				ps.setString(1, DEFAULT_MANAGER_NAME);
				ps.executeUpdate();
			}

			try (java.sql.PreparedStatement ps = c.prepareStatement("UPDATE staff SET password=? WHERE id=?")) {
				ps.setString(1, "m1");
				ps.setString(2, "m1");
				ps.executeUpdate();
			}

			try (java.sql.PreparedStatement check = c.prepareStatement("SELECT 1 FROM staff WHERE id='d1' LIMIT 1");
					java.sql.ResultSet rs = check.executeQuery()) {
				if (!rs.next()) {
					svc.addDoctor("d1", "Doctor One");
					try (java.sql.PreparedStatement pw = c
							.prepareStatement("UPDATE staff SET password='d1' WHERE id='d1'")) {
						pw.executeUpdate();
					}
				}
			}

			try (java.sql.PreparedStatement check = c.prepareStatement("SELECT 1 FROM staff WHERE id='n1' LIMIT 1");
					java.sql.ResultSet rs = check.executeQuery()) {
				if (!rs.next()) {
					svc.addNurse("n1", "Nurse One");
					try (java.sql.PreparedStatement pw = c
							.prepareStatement("UPDATE staff SET password='n1' WHERE id='n1'")) {
						pw.executeUpdate();
					}
				}
			}
		} catch (Exception ignore) {
		}
	}

	private static int bedsInRoom(int room) {
		return switch (room) {
		case 1 -> 1;
		case 2 -> 2;
		default -> 4;
		};
	}

	private void ensureBedsLayout() {
		String[] wards = { "A", "B" };
		try (java.sql.Connection c = rmit.s4134401.carehome.util.DB.get()) {
			for (String w : wards) {
				for (int room = 1; room <= 6; room++) {
					int need = bedsInRoom(room);
					for (int bed = 1; bed <= need; bed++) {
						try (java.sql.PreparedStatement chk = c.prepareStatement(
								"SELECT id FROM beds WHERE ward=? AND room=? AND bed_num=? LIMIT 1")) {
							chk.setString(1, w);
							chk.setInt(2, room);
							chk.setInt(3, bed);
							try (java.sql.ResultSet rs = chk.executeQuery()) {
								if (!rs.next()) {
									try (java.sql.PreparedStatement ins = c
											.prepareStatement("INSERT INTO beds(ward, room, bed_num) VALUES(?,?,?)")) {
										ins.setString(1, w);
										ins.setInt(2, room);
										ins.setInt(3, bed);
										ins.executeUpdate();
									}
								}
							}
						}
					}
				}
			}
		} catch (Exception ignore) {
		}
	}

	private void ensureCoreTables() {
		try (java.sql.Connection c = rmit.s4134401.carehome.util.DB.get();
				java.sql.Statement st = c.createStatement()) {

			st.execute("""
					    CREATE TABLE IF NOT EXISTS nurse_shifts(
					      id INTEGER PRIMARY KEY AUTOINCREMENT,
					      nurse_id TEXT NOT NULL,
					      day TEXT NOT NULL,
					      start TEXT NOT NULL,
					      end TEXT NOT NULL
					    )
					""");

			st.execute("""
					    CREATE TABLE IF NOT EXISTS doctor_minutes(
					      id INTEGER PRIMARY KEY AUTOINCREMENT,
					      doctor_id TEXT NOT NULL,
					      day TEXT NOT NULL,
					      minutes INTEGER NOT NULL
					    )
					""");
		} catch (Exception ignore) {
		}
	}

	@Override
	public void start(Stage stage) {
		rmit.s4134401.carehome.util.DB.init("carehome.db");
		ensureCoreTables();
		ensureBedsLayout();
		ensureAuditTable();

		svc = new CareHomeService(new JdbcStaffRepository(), new JdbcBedRepository(), new JdbcPatientRepository(),
				new JdbcNurseRosterRepository(), new JdbcDoctorMinutesRepository(), new JdbcAuditRepository(),
				new JdbcPrescriptionRepository(), new JdbcAdministrationRepository());
		try {
			svc.addManager(DEFAULT_MANAGER_ID, DEFAULT_MANAGER_NAME);
		} catch (RuntimeException ignore) {
		}
		ensureBootstrapManagerPassword();
		ensureDemoAccounts();

		if (!promptLogin()) {
			Platform.exit();
			return;
		}

		BorderPane root = new BorderPane();
		MenuBar mb = buildMenuBar(stage);
		root.setTop(mb);
		root.setCenter(buildBedPane());
		root.setBottom(buildStatusBar());

		applyRolePermissions();
		updateUserBadge();

		Scene scene = new Scene(root, 1000, 650);
		stage.setTitle("Resident HealthCare System — S2 2025");
		stage.setScene(scene);
		stage.setOnCloseRequest(ev -> {
			try {
				savePatientsSnapshot("patients_snapshot.json");
			} catch (Exception ignore) {
			}
		});
		stage.show();

		setStatus("Logged in: " + currentUserId + " (" + currentUserRole + ")");
		refreshBeds();
	}

	private boolean promptLogin() {
		while (true) {
			Dialog<Credentials> d = new Dialog<>();
			d.setTitle("Login");
			d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

			TextField tfId = new TextField();
			tfId.setPromptText("Staff ID (e.g., m1, d1, n1)");
			PasswordField tfPw = new PasswordField();
			tfPw.setPromptText("Password");

			Label demo = new Label("Demo accounts for testing:\n" + "Manager  →  ID: m1   Password: m1\n"
					+ "Doctor   →  ID: d1   Password: d1\n" + "Nurse    →  ID: n1   Password: n1");
			demo.setWrapText(true);
			demo.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");

			GridPane form = formGrid(new Label("Staff ID:"), tfId, new Label("Password:"), tfPw);
			VBox box = new VBox(8, form, demo);
			box.setPadding(new Insets(10));

			d.getDialogPane().setContent(box);
			d.setResultConverter(
					btn -> btn == ButtonType.OK ? new Credentials(tfId.getText().trim(), tfPw.getText()) : null);

			java.util.Optional<Credentials> res = d.showAndWait();
			if (!res.isPresent())
				return false;

			String id = res.get().id;
			String pw = res.get().pw;

			Role role = authenticate(id, pw);
			if (role == null) {
				showError("Login failed", "Invalid ID or password.");
				continue;
			}

			currentUserId = id;
			currentUserRole = role;

			Platform.runLater(() -> {
				applyRolePermissions();
				updateUserBadge();
			});

			return true;
		}
	}

	private static final class Credentials {
		final String id;
		final String pw;

		Credentials(String i, String p) {
			id = i;
			pw = p;
		}
	}

	private Role getRoleFor(String id) {
		String sql = "SELECT role FROM staff WHERE id = ? LIMIT 1";
		try (java.sql.Connection c = rmit.s4134401.carehome.util.DB.get();
				java.sql.PreparedStatement ps = c.prepareStatement(sql)) {
			ps.setString(1, id.trim());
			try (java.sql.ResultSet rs = ps.executeQuery()) {
				if (!rs.next())
					return null;
				return Role.valueOf(rs.getString(1));
			}
		} catch (Exception e) {
			showError("DB Error", e.getMessage());
			return null;
		}
	}

	private Role authenticate(String id, String pw) {
		String sql = "SELECT role FROM staff WHERE id=? AND password=? LIMIT 1";
		try (java.sql.Connection c = rmit.s4134401.carehome.util.DB.get();
				java.sql.PreparedStatement ps = c.prepareStatement(sql)) {
			ps.setString(1, id.trim());
			ps.setString(2, pw.trim());
			try (java.sql.ResultSet rs = ps.executeQuery()) {
				if (!rs.next())
					return null;
				return Role.valueOf(rs.getString("role"));
			}
		} catch (Exception e) {
			showError("DB Error", e.getMessage());
			return null;
		}
	}

	private void ensureBootstrapManagerPassword() {
		String sql = "UPDATE staff SET password=? WHERE id=? AND (password IS NULL OR TRIM(password)='')";
		try (java.sql.Connection c = rmit.s4134401.carehome.util.DB.get();
				java.sql.PreparedStatement ps = c.prepareStatement(sql)) {
			ps.setString(1, DEFAULT_MANAGER_BOOTSTRAP_PASSWORD);
			ps.setString(2, DEFAULT_MANAGER_ID);
			ps.executeUpdate();
		} catch (Exception ignore) {
		}
	}

	private void applyRolePermissions() {
		if (currentUserRole == null) {
			if (miSetDocMins != null)
				miSetDocMins.setDisable(true);
			if (miRemoveDocMins != null)
				miRemoveDocMins.setDisable(true);
			if (miListStaff != null)
				miListStaff.setDisable(true);
			if (miShowRoster != null)
				miShowRoster.setDisable(true);
			if (miStaffAudit != null)
				miStaffAudit.setDisable(true);
			if (miShowStaff != null)
				miShowStaff.setDisable(true);

			if (miAssignShift != null)
				miAssignShift.setDisable(true);
			if (miRemoveShift != null)
				miRemoveShift.setDisable(true);
			if (miCheckCompliance != null)
				miCheckCompliance.setDisable(true);
			if (miNurseWeek != null)
				miNurseWeek.setDisable(true);
			if (miDoctorWeek != null)
				miDoctorWeek.setDisable(true);
			if (miTeamWeek != null)
				miTeamWeek.setDisable(true);
			if (miRosterDashboard != null)
				miRosterDashboard.setDisable(true);
			if (miExportRoster != null)
				miExportRoster.setDisable(true);

			if (miAdmit != null)
				miAdmit.setDisable(true);
			if (miMove != null)
				miMove.setDisable(true);
			if (miAdminMed != null)
				miAdminMed.setDisable(true);
			if (miAddRx != null)
				miAddRx.setDisable(true);
			if (miDetails != null)
				miDetails.setDisable(true);
			if (miDischarge != null)
				miDischarge.setDisable(true);

			if (miResetPw != null)
				miResetPw.setDisable(true);
			if (miChangePw != null)
				miChangePw.setDisable(true);
			if (miUpdateResident != null)
				miUpdateResident.setDisable(true);

			if (miAddStaff != null)
				miAddStaff.setDisable(true);
			if (miModifyStaff != null)
				miModifyStaff.setDisable(true);
			return;
		}

		boolean isMgr = currentUserRole == Role.MANAGER;
		boolean isDoc = currentUserRole == Role.DOCTOR;
		boolean isNur = currentUserRole == Role.NURSE;

		// Staff admin (manager only)
		if (miAddStaff != null)
			miAddStaff.setDisable(!isMgr);
		if (miModifyStaff != null)
			miModifyStaff.setDisable(!isMgr);
		if (miSetDocMins != null)
			miSetDocMins.setDisable(!isMgr);
		if (miListStaff != null)
			miListStaff.setDisable(!isMgr);
		if (miShowRoster != null)
			miShowRoster.setDisable(!isMgr);
		if (miStaffAudit != null)
			miStaffAudit.setDisable(!isMgr);
		if (miShowStaff != null)
			miShowStaff.setDisable(!isMgr);
		if (miResetPw != null)
			miResetPw.setDisable(!isMgr);

		// Schedule (manager only)

		if (miAssignShift != null)
			miAssignShift.setDisable(!isMgr);
		if (miRemoveShift != null)
			miRemoveShift.setDisable(!isMgr);
		if (miSetDocMins != null)
			miSetDocMins.setDisable(!isMgr);
		if (miRemoveDocMins != null)
			miRemoveDocMins.setDisable(!isMgr);
		if (miCheckCompliance != null)
			miCheckCompliance.setDisable(!isMgr);
		if (miNurseWeek != null)
			miNurseWeek.setDisable(!isMgr);
		if (miDoctorWeek != null)
			miDoctorWeek.setDisable(!isMgr);
		if (miTeamWeek != null)
			miTeamWeek.setDisable(!isMgr);
		if (miRosterDashboard != null)
			miRosterDashboard.setDisable(!isMgr);
		if (miExportRoster != null)
			miExportRoster.setDisable(!isMgr);

		// Patients
		if (miAdmit != null)
			miAdmit.setDisable(!isMgr);
		if (miMove != null)
			miMove.setDisable(!isNur);
		if (miAdminMed != null)
			miAdminMed.setDisable(!isNur);
		if (miAddRx != null)
			miAddRx.setDisable(!isDoc);
		if (miDischarge != null)
			miDischarge.setDisable(!isMgr);
		if (miDetails != null)
			miDetails.setDisable(false);
		if (miUpdateResident != null)
			miUpdateResident.setDisable(!(isMgr || isNur));

		if (miChangePw != null)
			miChangePw.setDisable(false);
	}

	private void promptAddAnyStaff() {
		if (currentUserRole != Role.MANAGER) {
			showError("Access denied", "Managers only.");
			return;
		}

		Dialog<Boolean> d = new Dialog<>();
		d.setTitle("Add Staff");
		d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		TextField managerId = new TextField(currentUserId);
		managerId.setEditable(false);
		ComboBox<Role> role = new ComboBox<>();
		role.getItems().addAll(Role.MANAGER, Role.DOCTOR, Role.NURSE);
		role.getSelectionModel().select(Role.NURSE);
		TextField id = new TextField();
		id.setPromptText("e.g., m2 / d2 / n2");
		TextField name = new TextField();
		name.setPromptText("Full name");
		PasswordField pw = new PasswordField();
		pw.setPromptText("Initial password");

		GridPane gp = formGrid(new Label("Manager ID (auth):"), managerId, new Label("Role:"), role,
				new Label("Staff ID:"), id, new Label("Name:"), name, new Label("Password:"), pw);
		d.getDialogPane().setContent(gp);
		d.setResultConverter(btn -> btn == ButtonType.OK);

		d.showAndWait().ifPresent(ok -> {
			if (!ok)
				return;
			if (id.getText().trim().isEmpty() || name.getText().trim().isEmpty() || pw.getText().trim().isEmpty()) {
				showError("Add Staff", "ID, name and password are required.");
				return;
			}
			try {
				Role r = role.getValue();
				switch (r) {
				case MANAGER -> svc.addManager(id.getText().trim(), name.getText().trim());
				case DOCTOR -> svc.addDoctor(id.getText().trim(), name.getText().trim());
				case NURSE -> svc.addNurse(id.getText().trim(), name.getText().trim());
				}
				try (java.sql.Connection c = rmit.s4134401.carehome.util.DB.get();
						java.sql.PreparedStatement ps = c.prepareStatement("UPDATE staff SET password=? WHERE id=?")) {
					ps.setString(1, pw.getText().trim());
					ps.setString(2, id.getText().trim());
					ps.executeUpdate();
				}
				setStatus("Added " + r + ": " + id.getText().trim());
				audit("ADD_STAFF_UI", "role=" + r + " id=" + id.getText().trim());
			} catch (Exception ex) {
				showError("Add Staff failed", ex.getMessage());
			}

		});

	}

	private void promptModifyStaff() {
		if (currentUserRole != Role.MANAGER) {
			showError("Access denied", "Managers only.");
			return;
		}

		Dialog<Boolean> d = new Dialog<>();
		d.setTitle("Modify Staff Details");
		d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		TextField staffId = new TextField();
		staffId.setPromptText("Enter existing staff ID (e.g., m1, d1, n1)");

		TextField name = new TextField();
		name.setPromptText("Full name");
		ComboBox<Role> role = new ComboBox<>();
		role.getItems().addAll(Role.values());
		PasswordField newPw = new PasswordField();
		newPw.setPromptText("Leave blank to keep current password");

		ComboBox<DayOfWeek> nsDay = new ComboBox<>();
		nsDay.getItems().addAll(DayOfWeek.values());
		nsDay.getSelectionModel().select(DayOfWeek.MONDAY);
		ComboBox<String> nsShift = new ComboBox<>();
		nsShift.getItems().addAll("A (08-16)", "B (14-22)");
		nsShift.getSelectionModel().select(0);
		Button btnAssign = new Button("Assign Shift");
		Button btnRemove = new Button("Remove Shift");

		ComboBox<DayOfWeek> dmDay = new ComboBox<>();
		dmDay.getItems().addAll(DayOfWeek.values());
		dmDay.getSelectionModel().select(DayOfWeek.MONDAY);
		TextField dmMinutes = new TextField("60");
		Button btnSetMinutes = new Button("Set Doctor Minutes");

		staffId.focusedProperty().addListener((o, was, isNow) -> {
			if (!isNow && !staffId.getText().trim().isEmpty()) {
				String sid = staffId.getText().trim();
				try (java.sql.Connection c = rmit.s4134401.carehome.util.DB.get();
						java.sql.PreparedStatement ps = c
								.prepareStatement("SELECT name, role FROM staff WHERE id=? LIMIT 1")) {
					ps.setString(1, sid);
					try (java.sql.ResultSet rs = ps.executeQuery()) {
						if (rs.next()) {
							name.setText(rs.getString(1));
							role.getSelectionModel().select(Role.valueOf(rs.getString(2)));
						} else {
							name.clear();
						}
					}
				} catch (Exception ignore) {
				}
			}
		});

		btnAssign.setOnAction(e -> {
			try {
				if (!nurseExists(staffId.getText())) {
					showError("Assign Shift", "Nurse '" + staffId.getText() + "' does not exist.");
					return;
				}
				boolean shiftA = nsShift.getSelectionModel().getSelectedIndex() == 0;
				svc.assignNurseShift(currentUserId, staffId.getText().trim(), nsDay.getValue(), shiftA);
				setStatus("Shift assigned to " + staffId.getText().trim());
			} catch (Exception ex) {
				showError("Assign Shift", ex.getMessage());
			}
		});

		btnRemove.setOnAction(e -> {
			try {
				if (!nurseExists(staffId.getText())) {
					showError("Remove Shift", "Nurse '" + staffId.getText() + "' does not exist.");
					return;
				}
				boolean shiftA = nsShift.getSelectionModel().getSelectedIndex() == 0;
				svc.removeNurseShift(currentUserId, staffId.getText().trim(), nsDay.getValue(), shiftA);
				setStatus("Shift removed for " + staffId.getText().trim());
			} catch (Exception ex) {
				showError("Remove Shift", ex.getMessage());
			}
		});

		btnSetMinutes.setOnAction(e -> {
			try {
				String docId = staffId.getText().trim();
				if (!doctorExists(docId)) {
					showError("Doctor Minutes", "Doctor '" + docId + "' does not exist.");
					return;
				}
				int mins = Integer.parseInt(dmMinutes.getText().trim());
				svc.setDoctorMinutes(currentUserId, staffId.getText().trim(), dmDay.getValue(),
						Integer.parseInt(dmMinutes.getText().trim()));
				setStatus("Doctor minutes set for " + docId + " on " + dmDay.getValue() + " = " + mins);
			} catch (NumberFormatException nfe) {
				showError("Doctor Minutes", "Enter a number in Minutes.");
			} catch (Exception ex) {
				showError("Doctor Minutes", ex.getMessage());
			}
		});

		GridPane gp = new GridPane();
		gp.setHgap(10);
		gp.setVgap(10);
		gp.setPadding(new Insets(12));
		int r = 0;
		gp.add(new Label("Staff ID:"), 0, r);
		gp.add(staffId, 1, r++);
		gp.add(new Label("Name:"), 0, r);
		gp.add(name, 1, r++);
		gp.add(new Label("Role:"), 0, r);
		gp.add(role, 1, r++);
		gp.add(new Label("New Password:"), 0, r);
		gp.add(newPw, 1, r++);

		TitledPane nursePane = new TitledPane("Nurse Shifts",
				new VBox(8, new HBox(8, new Label("Day:"), nsDay, new Label("Shift:"), nsShift),
						new HBox(8, btnAssign, btnRemove)));
		nursePane.setExpanded(false);

		TitledPane docPane = new TitledPane("Doctor Minutes",
				new VBox(8, new HBox(8, new Label("Day:"), dmDay, new Label("Minutes:"), dmMinutes), btnSetMinutes));
		docPane.setExpanded(false);

		VBox content = new VBox(12, gp);
		d.getDialogPane().setContent(content);

		d.setResultConverter(btn -> btn == ButtonType.OK);

		d.showAndWait().ifPresent(ok -> {
			if (!ok)
				return;
			try (java.sql.Connection c = rmit.s4134401.carehome.util.DB.get()) {
				try (java.sql.PreparedStatement ps = c.prepareStatement("UPDATE staff SET name=?, role=? WHERE id=?")) {
					ps.setString(1, name.getText().trim());
					ps.setString(2, role.getValue().name());
					ps.setString(3, staffId.getText().trim());
					int rows = ps.executeUpdate();
					if (rows == 0) {
						showError("Modify Staff", "No such staff: " + staffId.getText().trim());
						return;
					}
				}
				if (!newPw.getText().trim().isEmpty()) {
					try (java.sql.PreparedStatement ps = c.prepareStatement("UPDATE staff SET password=? WHERE id=?")) {
						ps.setString(1, newPw.getText().trim());
						ps.setString(2, staffId.getText().trim());
						ps.executeUpdate();
					}
				}
				setStatus("Updated staff: " + staffId.getText().trim());
				if (staffId.getText().trim().equals(currentUserId))
					updateUserBadge();
			} catch (Exception ex) {
				showError("Modify Staff", ex.getMessage());
			}
		});

		audit("MODIFY_STAFF", "staff_id=" + staffId.getText().trim() + " role=" + role.getValue().name()
				+ (newPw.getText().trim().isEmpty() ? "" : " [password changed]"));

	}

	private String nurseWeeklyRosterSummaryFromDB() {
		StringBuilder sb = new StringBuilder("--- Nurse roster (week) ---\n");
		for (DayOfWeek d : DayOfWeek.values()) {
			sb.append("\n== ").append(d).append(" ==\n");
			String sql = "SELECT nurse_id, start, end FROM nurse_shifts WHERE day=? ORDER BY nurse_id";
			try (java.sql.Connection c = rmit.s4134401.carehome.util.DB.get();
					java.sql.PreparedStatement ps = c.prepareStatement(sql)) {
				ps.setString(1, d.name());
				try (java.sql.ResultSet rs = ps.executeQuery()) {
					boolean any = false;
					while (rs.next()) {
						any = true;
						sb.append(rs.getString("nurse_id")).append("  ").append(rs.getString("start")).append("–")
								.append(rs.getString("end")).append('\n');
					}
					if (!any)
						sb.append("(no nurse on roster)\n");
				}
			} catch (Exception e) {
				sb.append("[error: ").append(e.getMessage()).append("]\n");
			}
		}
		return sb.toString();
	}

	private String doctorMinutesSummaryFromDB() {
		StringBuilder sb = new StringBuilder("--- Doctor coverage (minutes per day) ---\n");

		String totalsSql = """
				    SELECT day, SUM(minutes) AS mins
				    FROM doctor_minutes
				    WHERE doctor_id IS NOT NULL AND TRIM(doctor_id) <> ''
				    GROUP BY day
				""";
		Map<String, Integer> totals = new HashMap<>();
		try (var c = rmit.s4134401.carehome.util.DB.get();
				var st = c.createStatement();
				var rs = st.executeQuery(totalsSql)) {
			while (rs.next())
				totals.put(rs.getString("day"), rs.getInt("mins"));
		} catch (Exception ignore) {
		}

		String perDocSql = """
				    SELECT dm.day, dm.doctor_id, COALESCE(s.name,'(no name)') AS name, SUM(dm.minutes) AS mins
				    FROM doctor_minutes dm
				    JOIN staff s ON s.id = dm.doctor_id AND s.role='DOCTOR'
				    WHERE dm.doctor_id IS NOT NULL AND TRIM(dm.doctor_id) <> ''
				    GROUP BY dm.day, dm.doctor_id, s.name
				    ORDER BY dm.day, dm.doctor_id
				""";
		Map<String, List<String>> perDay = new HashMap<>();
		try (var c = rmit.s4134401.carehome.util.DB.get();
				var st = c.createStatement();
				var rs = st.executeQuery(perDocSql)) {
			while (rs.next()) {
				String day = rs.getString("day");
				String id = rs.getString("doctor_id");
				String nm = rs.getString("name");
				int m = rs.getInt("mins");
				perDay.computeIfAbsent(day, k -> new ArrayList<>()).add("  - " + id + "  " + nm + " : " + m + " min");
			}
		} catch (Exception ignore) {
		}

		for (DayOfWeek d : DayOfWeek.values()) {
			int m = totals.getOrDefault(d.name(), 0);
			sb.append(d).append(" : ").append(m).append(" min ").append(m < 60 ? "[Below 60]" : "").append('\n');
			List<String> lines = perDay.get(d.name());
			if (lines != null)
				for (String ln : lines)
					sb.append(ln).append('\n');
		}
		return sb.toString();
	}

	private String teamRosterSummaryFromDB() {
		return doctorMinutesSummaryFromDB() + "\n\n" + nurseWeeklyRosterSummaryFromDB();
	}

	private boolean doctorOnDuty(String docId, DayOfWeek day, int requiredMinutes) {
		String sql = "SELECT COALESCE(SUM(minutes),0) FROM doctor_minutes WHERE doctor_id=? AND day=?";
		try (java.sql.Connection c = rmit.s4134401.carehome.util.DB.get();
				java.sql.PreparedStatement ps = c.prepareStatement(sql)) {
			ps.setString(1, docId.trim());
			ps.setString(2, day.name());
			try (java.sql.ResultSet rs = ps.executeQuery()) {
				int mins = rs.next() ? rs.getInt(1) : 0;
				return mins >= requiredMinutes;
			}
		} catch (Exception e) {
			showError("DB Error", e.getMessage());
			return false;
		}
	}

	private void promptUpdateResident() {
		Dialog<Boolean> d = new Dialog<>();
		d.setTitle("Update Resident");
		d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		ComboBox<String> ward = new ComboBox<>();
		ward.getItems().addAll("A", "B");
		ward.getSelectionModel().select("A");

		Spinner<Integer> room = new Spinner<>(1, 6, 1);
		Spinner<Integer> bed = new Spinner<>(1, 4, 1);

		TextField name = new TextField();
		name.setPromptText("Full name");

		ComboBox<Gender> gender = new ComboBox<>();
		gender.getItems().addAll(Gender.values());
		gender.getSelectionModel().select(Gender.F);

		CheckBox iso = new CheckBox("Requires isolation");

		java.util.function.Consumer<Void> preload = v -> {
			try {
				String pid = svc.patientIdInBed(ward.getValue(), room.getValue(), bed.getValue());
				if (pid == null) {
					name.clear();
					iso.setSelected(false);
					return;
				}
				try (java.sql.Connection c = rmit.s4134401.carehome.util.DB.get();
						java.sql.PreparedStatement ps = c
								.prepareStatement("SELECT full_name, gender, isolation FROM patients WHERE id=?")) {
					ps.setString(1, pid);
					try (java.sql.ResultSet rs = ps.executeQuery()) {
						if (rs.next()) {
							name.setText(rs.getString("full_name"));
							String g = rs.getString("gender");
							if (g != null)
								gender.getSelectionModel().select(Gender.valueOf(g));
							iso.setSelected(rs.getInt("isolation") == 1);
						}
					}
				}
			} catch (Exception ignore) {
			}
		};

		ward.focusedProperty().addListener((o, a, b) -> {
			if (!b)
				preload.accept(null);
		});
		room.focusedProperty().addListener((o, a, b) -> {
			if (!b)
				preload.accept(null);
		});
		bed.focusedProperty().addListener((o, a, b) -> {
			if (!b)
				preload.accept(null);
		});

		GridPane gp = formGrid(new Label("Ward:"), ward, new Label("Room (1-6):"), room, new Label("Bed (1-4):"), bed,
				new Label("Full name:"), name, new Label("Gender:"), gender, new Label("Isolation:"), iso);
		d.getDialogPane().setContent(gp);
		d.setResultConverter(btn -> btn == ButtonType.OK);

		d.showAndWait().ifPresent(ok -> {
			if (!ok)
				return;
			try {
				String pid = svc.patientIdInBed(ward.getValue(), room.getValue(), bed.getValue());
				if (pid == null) {
					showError("Update Resident", "Selected bed is vacant.");
					return;
				}
				try (java.sql.Connection c = rmit.s4134401.carehome.util.DB.get();
						java.sql.PreparedStatement ps = c.prepareStatement(
								"UPDATE patients SET full_name=?, gender=?, isolation=? WHERE id=?")) {
					ps.setString(1, name.getText().trim());
					ps.setString(2, gender.getValue().name());
					ps.setInt(3, iso.isSelected() ? 1 : 0);
					ps.setString(4, pid);
					ps.executeUpdate();
				}
				info("Update Resident", "Updated " + pid);
				audit("UPDATE_RESIDENT", "pid=" + pid + " name=" + name.getText().trim() + " gender="
						+ gender.getValue().name() + " iso=" + (iso.isSelected() ? "1" : "0"));
				refreshBeds();
			} catch (Exception ex) {
				showError("Update Resident", ex.getMessage());
			}
		});
	}

	private void showRosterDashboard() {
		TabPane tabs = new TabPane();

		TextArea team = new TextArea(teamRosterSummaryFromDB());
		team.setEditable(false);
		team.setWrapText(true);
		BorderPane p1 = new BorderPane(team);
		Tab t1 = new Tab("Team (Week)", p1);
		t1.setClosable(false);

		TextArea docs = new TextArea(doctorMinutesSummaryFromDB());
		docs.setEditable(false);
		docs.setWrapText(true);
		BorderPane p2 = new BorderPane(docs);
		Tab t2 = new Tab("Doctor Coverage", p2);
		t2.setClosable(false);

		tabs.getTabs().setAll(t1, t2);

		Dialog<Void> d = new Dialog<>();
		d.setTitle("Roster Dashboard");
		d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
		d.getDialogPane().setContent(tabs);
		d.setResizable(true);
		d.getDialogPane().setPrefSize(1000, 700);
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

		BedCell(int id, String ward, int room, int bedNum, String patientId, Gender gender, boolean isolation) {
			this.id = id;
			this.ward = ward;
			this.room = room;
			this.bedNum = bedNum;
			this.patientId = patientId;
			this.gender = gender;
			this.isolation = isolation;
		}

		boolean isVacant() {
			return patientId == null;
		}

		String cellLabel() {
			String base = ward + "-R" + room + "-B" + bedNum;
			return isVacant() ? base + "\n[vacant]" : base + "\n" + patientId;
		}
	}

	private java.util.List<BedCell> loadBedCellsFromDB() {
		java.util.List<BedCell> out = new java.util.ArrayList<>();
		String sql = "SELECT b.id, b.ward, b.room, b.bed_num, p.id AS pid, p.gender, p.isolation " + "FROM beds b "
				+ "LEFT JOIN patients p ON p.bed_id = b.id " + "ORDER BY b.ward, b.room, b.bed_num";
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
		} catch (Exception e) {
			showError("DB Error", e.getMessage());
		}
		return out;
	}

	private void archivePatientToFile(String patientId) {
		try {
			Files.createDirectories(Path.of("archive"));
			String fname = "archive/" + patientId + "_" + System.currentTimeMillis() + ".txt";
			StringBuilder out = new StringBuilder();

			out.append("Archived: ").append(OffsetDateTime.now()).append("\n");
			out.append("Patient: ").append(patientId).append("\n\n");

			try (var c = rmit.s4134401.carehome.util.DB.get();
					var ps = c.prepareStatement("SELECT full_name, gender, isolation FROM patients WHERE id=?")) {
				ps.setString(1, patientId);
				try (var rs = ps.executeQuery()) {
					if (rs.next()) {
						out.append("Name: ").append(rs.getString(1)).append("\n");
						out.append("Gender: ").append(rs.getString(2)).append("\n");
						out.append("Isolation: ").append(rs.getInt(3) == 1 ? "YES" : "NO").append("\n\n");
					}
				}
			}

			out.append("=== PRESCRIPTIONS ===\n");
			try (var c = rmit.s4134401.carehome.util.DB.get(); var ps = c.prepareStatement("""
					    SELECT p.id, p.day, p.doctor_id, l.medicine, l.dose, l.times
					    FROM prescriptions p
					    LEFT JOIN prescription_lines l ON l.prescription_id = p.id
					    WHERE p.patient_id=?
					    ORDER BY p.id, l.id
					""")) {
				ps.setString(1, patientId);
				try (var rs = ps.executeQuery()) {
					while (rs.next()) {
						out.append("RX#").append(rs.getInt(1)).append("  day=").append(rs.getString(2))
								.append("  doctor=").append(rs.getString(3)).append("  ").append(rs.getString(4))
								.append("  ").append(rs.getString(5)).append("  @ ").append(rs.getString(6))
								.append("\n");
					}
				}
			}

			out.append("\n=== ADMINISTRATIONS ===\n");
			try (var c = rmit.s4134401.carehome.util.DB.get(); var ps = c.prepareStatement("""
					    SELECT medicine, dose, day, time, staff_id, is_update
					    FROM administrations
					    WHERE patient_id=?
					    ORDER BY id
					""")) {
				ps.setString(1, patientId);
				try (var rs = ps.executeQuery()) {
					while (rs.next()) {
						out.append(rs.getString(3)).append(" ").append(rs.getString(4)).append("  ")
								.append(rs.getString(1)).append("  ").append(rs.getString(2)).append("  by ")
								.append(rs.getString(5)).append(rs.getInt(6) == 1 ? "  [UPDATE]\n" : "\n");
					}
				}
			}

			Files.write(Path.of(fname), out.toString().getBytes(StandardCharsets.UTF_8));
			audit("ARCHIVE", "Archived " + patientId + " -> " + fname);
		} catch (Exception e) {
		}
	}

	private MenuBar buildMenuBar(Stage stage) {
		Menu file = new Menu("File");
		MenuItem save = new MenuItem("Save…");
		save.setOnAction(e -> {
			try {
				app.saveToFile("carehome.bin");
				setStatus("Saved to carehome.bin");
			} catch (RuntimeException ex) {
				showError("Save failed", ex.getMessage());
			}
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

		miSwitchUser = new MenuItem("Switch User…");
		miSwitchUser.setOnAction(e -> switchUser(stage));

		MenuItem exportLogs = new MenuItem("Export Logs…");
		exportLogs.setOnAction(e -> exportAuditToFile());

		MenuItem restoreSnap = new MenuItem("Restore Snapshot…");
		restoreSnap.setOnAction(e -> restorePatientsSnapshot("patients_snapshot.json"));

		file.getItems().setAll(miSwitchUser, new SeparatorMenuItem(), exportLogs, restoreSnap, new SeparatorMenuItem(),
				exit);

		file.getItems().setAll(miSwitchUser, new SeparatorMenuItem(), exportLogs, new SeparatorMenuItem(), exit);

		Menu staff = new Menu("Staff");

		miAddStaff = new MenuItem("Add Staff…");
		miAddStaff.setOnAction(e -> promptAddAnyStaff());

		miModifyStaff = new MenuItem("Modify Staff Details…");
		miModifyStaff.setOnAction(e -> promptModifyStaff());

		miShowStaff = new MenuItem("Show Staff…");
		miShowStaff.setOnAction(e -> showStaffList());

		miChangePw = new MenuItem("Change My Password...");
		miChangePw.setOnAction(e -> promptChangePassword());

		miResetPw = new MenuItem("Reset Another User’s Password…");
		miResetPw.setOnAction(e -> promptResetUserPassword());

		staff.getItems().setAll(miAddStaff, new SeparatorMenuItem(), miModifyStaff, new SeparatorMenuItem(),
				miShowStaff, new SeparatorMenuItem(), miChangePw, miResetPw);

		Menu schedule = new Menu("Schedule");
		miAssignShift = new MenuItem("Assign Nurse Shift…");
		miAssignShift.setOnAction(e -> promptAssignShift());
		miRemoveShift = new MenuItem("Remove Nurse Shift…");
		miRemoveShift.setOnAction(e -> promptRemoveShift());

		miSetDocMins = new MenuItem("Set Doctor Minutes…");
		miSetDocMins.setOnAction(e -> promptSetDoctorMinutes());

		miRemoveDocMins = new MenuItem("Remove Doctor Minutes…");
		miRemoveDocMins.setOnAction(e -> promptRemoveDoctorMinutes());

		miRosterDashboard = new MenuItem("Roster Dashboard…");
		miRosterDashboard.setOnAction(e -> showRosterDashboard());

		miExportRoster = new MenuItem("Export Week to roster_week.txt");
		miExportRoster.setOnAction(e -> {
			try {
				String text = teamRosterSummaryFromDB();
				java.nio.file.Files.write(java.nio.file.Paths.get("roster_week.txt"),
						text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
				info("Export", "Saved roster_week.txt in the app folder.");
				setStatus("Exported roster_week.txt");
			} catch (Exception ex) {
				showError("Export failed", ex.getMessage());
			}
		});

		miCheckCompliance = new MenuItem("Check Compliance");
		miCheckCompliance.setOnAction(e -> {
			try {
				svc.checkCompliance();
				info("Compliance", "All good");
				setStatus("Compliance OK");
			} catch (Exception ex) {
				showError("Compliance failure", ex.getMessage());
			}
		});

		schedule.getItems().setAll(miAssignShift, miRemoveShift, new SeparatorMenuItem(), miSetDocMins, miRemoveDocMins,
				miRosterDashboard, miExportRoster, new SeparatorMenuItem(), miCheckCompliance);

		Menu patients = new Menu("Patients");
		miAdmit = new MenuItem("Admit…");
		miAdmit.setOnAction(e -> promptAdmitPatient());
		miMove = new MenuItem("Move…");
		miMove.setOnAction(e -> promptMovePatient());
		miDischarge = new MenuItem("Discharge…");
		miDischarge.setOnAction(e -> promptDischargePatient());
		miDetails = new MenuItem("Resident Details…");
		miDetails.setOnAction(e -> promptResidentDetails());
		miAddRx = new MenuItem("Add Prescription…");
		miAddRx.setOnAction(e -> promptAddPrescription());
		miAdminMed = new MenuItem("Administer Medication…");
		miAdminMed.setOnAction(e -> promptAdministerMedication());
		miUpdateResident = new MenuItem("Update Resident…");
		miUpdateResident.setOnAction(e -> promptUpdateResident());

		MenuItem miCurrentPatients = new MenuItem("Current Patients…");
		miCurrentPatients.setOnAction(e -> showCurrentPatients());

		patients.getItems().addAll(miAdmit, miMove, miDischarge, new SeparatorMenuItem(), miDetails, miAddRx,
				miAdminMed, new SeparatorMenuItem(), miUpdateResident, miCurrentPatients);

		Menu audit = new Menu("Audit");
		MenuItem miViewLogs = new MenuItem("View Logs…");
		miViewLogs.setOnAction(e -> showAuditLog());
		MenuItem miExportLogs = new MenuItem("Export Logs…");
		miExportLogs.setOnAction(e -> exportAuditToFile());
		audit.getItems().setAll(miViewLogs, miExportLogs);

		return new MenuBar(file, staff, schedule, patients, audit);

	}

	private String nextPatientId() {
		int max = 0;
		String sql = "SELECT id FROM patients";
		try (java.sql.Connection c = rmit.s4134401.carehome.util.DB.get();
				java.sql.Statement st = c.createStatement();
				java.sql.ResultSet rs = st.executeQuery(sql)) {
			while (rs.next()) {
				String id = rs.getString(1);
				if (id != null && id.startsWith("p")) {
					try {
						int n = Integer.parseInt(id.substring(1));
						if (n > max)
							max = n;
					} catch (NumberFormatException ignore) {
					}
				}
			}
		} catch (Exception ignore) {
		}
		return "p" + (max + 1);
	}

	private String safeGetString(java.sql.ResultSet rs, String col) {
		try {
			return rs.getString(col);
		} catch (Exception ignore) {
			return null;
		}
	}

	private void showAuditLog() {
		String sql = "SELECT * FROM audit ORDER BY rowid DESC LIMIT 200";
		StringBuilder sb = new StringBuilder();
		try (java.sql.Connection c = rmit.s4134401.carehome.util.DB.get();
				java.sql.Statement st = c.createStatement();
				java.sql.ResultSet rs = st.executeQuery(sql)) {

			java.sql.ResultSetMetaData md = rs.getMetaData();
			int n = md.getColumnCount();

			for (int i = 1; i <= n; i++) {
				if (i > 1)
					sb.append(" | ");
				sb.append(md.getColumnLabel(i));
			}
			sb.append("\n").append("-".repeat(80)).append("\n");

			while (rs.next()) {
				for (int i = 1; i <= n; i++) {
					if (i > 1)
						sb.append(" | ");
					sb.append(String.valueOf(rs.getObject(i)));
				}
				sb.append("\n");
			}
		} catch (Exception e) {
			showError("Audit", "Could not read audit table: " + e.getMessage());
			return;
		}
		if (sb.length() == 0)
			sb.append("(no audit entries)");
		showLongInfo("Audit (latest first)", sb.toString());
	}

	private void showCurrentPatients() {
		StringBuilder sb = new StringBuilder();
		String sql = "SELECT b.ward, b.room, b.bed_num, p.* " + "FROM beds b LEFT JOIN patients p ON p.bed_id=b.id "
				+ "WHERE p.id IS NOT NULL " + "ORDER BY b.ward, b.room, b.bed_num";
		try (java.sql.Connection c = rmit.s4134401.carehome.util.DB.get();
				java.sql.Statement st = c.createStatement();
				java.sql.ResultSet rs = st.executeQuery(sql)) {
			while (rs.next()) {
				String ward = rs.getString("ward");
				int room = rs.getInt("room");
				int bed = rs.getInt("bed_num");
				String pid = rs.getString("id");
				String g = safeGetString(rs, "gender");
				String nm = safeGetString(rs, "full_name");

				boolean iso = false;
				try {
					iso = rs.getInt("isolation") == 1;
				} catch (Exception ignore) {
				}

				String loc = ward + "-R" + room + "-B" + bed;
				String line = String.format("%s  %-6s  %-1s  %s%s", loc, pid, (g == null ? "?" : g),
						(nm == null || nm.isBlank() ? "(no name on file)" : nm), iso ? "  [isolation]" : "");
				sb.append(line).append('\n');
			}
		} catch (Exception ex) {
			showError("Current Patients", ex.getMessage());
			return;
		}
		String body = sb.length() == 0 ? "(no occupied beds)" : sb.toString();
		showLongInfo("Current Patients", body);
	}

	private void switchUser(Stage stage) {
		currentUserId = null;
		currentUserRole = null;

		if (!promptLogin()) {
			Platform.runLater(() -> {
				applyRolePermissions();
				updateUserBadge();
				setStatus("Login cancelled");
			});
			return;
		}

		Platform.runLater(() -> {
			applyRolePermissions();
			updateUserBadge();
			setStatus("Logged in: " + currentUserId + " (" + currentUserRole + ")");
			refreshBeds();
		});
	}

	private void updateUserBadge() {
		if (currentUserId == null || currentUserRole == null) {
			userBadge.setText("Not signed in");
			return;
		}
		String nm = lookupStaffName(currentUserId);
		String who = (nm == null || nm.isBlank()) ? currentUserId : (nm + " (" + currentUserId + ")");
		userBadge.setText(who + " — " + currentUserRole);
	}

	private String lookupStaffName(String id) {
		String sql = "SELECT name FROM staff WHERE id=? LIMIT 1";
		try (java.sql.Connection c = rmit.s4134401.carehome.util.DB.get();
				java.sql.PreparedStatement ps = c.prepareStatement(sql)) {
			ps.setString(1, id);
			try (java.sql.ResultSet rs = ps.executeQuery()) {
				return rs.next() ? rs.getString(1) : null;
			}
		} catch (Exception ignore) {
			return null;
		}
	}

	private void promptResetUserPassword() {
		if (currentUserRole != Role.MANAGER) {
			showError("Access denied", "Managers only.");
			return;
		}

		Dialog<Triple<String, String, String>> d = new Dialog<>();
		d.setTitle("Reset User Password");
		d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		TextField targetId = new TextField();
		PasswordField pw1 = new PasswordField();
		PasswordField pw2 = new PasswordField();
		targetId.setPromptText("User ID (e.g., n1, d2)");
		pw1.setPromptText("New password");
		pw2.setPromptText("Re-enter new password");

		GridPane gp = formGrid(new Label("User ID:"), targetId, new Label("New password:"), pw1, new Label("Re-enter:"),
				pw2);
		d.getDialogPane().setContent(gp);
		d.setResultConverter(
				btn -> btn == ButtonType.OK ? new Triple<>(targetId.getText().trim(), pw1.getText(), pw2.getText())
						: null);

		d.showAndWait().ifPresent(t -> {
			if (!t.b.equals(t.c)) {
				showError("Reset Password", "Passwords do not match.");
				return;
			}
			if (t.a.isEmpty()) {
				showError("Reset Password", "User ID required.");
				return;
			}
			try (java.sql.Connection c = rmit.s4134401.carehome.util.DB.get();
					java.sql.PreparedStatement ps = c.prepareStatement("UPDATE staff SET password=? WHERE id=?")) {
				ps.setString(1, t.b.trim());
				ps.setString(2, t.a.trim());
				int rows = ps.executeUpdate();
				if (rows == 0) {
					showError("Reset Password", "No such user: " + t.a);
					return;
				}
				info("Reset Password", "Password updated for " + t.a);
			} catch (Exception ex) {
				showError("Reset Password", ex.getMessage());
			}
			audit("RESET_PASSWORD", "target=" + t.a);
		});

	}

	private void promptChangePassword() {
		Dialog<Triple<String, String, String>> d = new Dialog<>();
		d.setTitle("Change Password");
		d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		PasswordField oldPw = new PasswordField();
		PasswordField newPw1 = new PasswordField();
		PasswordField newPw2 = new PasswordField();
		oldPw.setPromptText("Current password");
		newPw1.setPromptText("New password");
		newPw2.setPromptText("Re-enter new password");

		GridPane gp = formGrid(new Label("Old password:"), oldPw, new Label("New password:"), newPw1,
				new Label("Re-enter new password:"), newPw2);
		d.getDialogPane().setContent(gp);
		d.setResultConverter(
				btn -> btn == ButtonType.OK ? new Triple<>(oldPw.getText(), newPw1.getText(), newPw2.getText()) : null);

		d.showAndWait().ifPresent(t -> {
			if (t == null)
				return;
			if (!t.b.equals(t.c)) {
				showError("Change Password", "New passwords do not match.");
				return;
			}

			String sql = "UPDATE staff SET password=? WHERE id=? AND password=?";
			try (java.sql.Connection c = rmit.s4134401.carehome.util.DB.get();
					java.sql.PreparedStatement ps = c.prepareStatement(sql)) {
				ps.setString(1, t.b.trim());
				ps.setString(2, currentUserId.trim());
				ps.setString(3, t.a.trim());
				int rows = ps.executeUpdate();
				if (rows == 0) {
					showError("Change Password", "Current password is incorrect.");
					return;
				}
				info("Change Password", "Password updated successfully!");
			} catch (Exception ex) {
				showError("Change Password", ex.getMessage());
			}
		});
	}

	private void promptAdmitPatient() {
		Dialog<Boolean> d = new Dialog<>();
		d.setTitle("Admit Patient");
		d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		TextField managerId = new TextField(currentUserId);
		managerId.setEditable(false);

		TextField tfAutoId = new TextField(nextPatientId());
		tfAutoId.setEditable(false);

		TextField name = new TextField();
		name.setPromptText("Full name (required)");

		ComboBox<Gender> gender = new ComboBox<>();
		gender.getItems().addAll(Gender.values());
		gender.getSelectionModel().select(Gender.F);

		CheckBox iso = new CheckBox("Requires isolation");

		ComboBox<String> ward = new ComboBox<>();
		ward.getItems().addAll("A", "B");
		ward.getSelectionModel().select("A");

		Spinner<Integer> room = new Spinner<>(1, 6, 1);
		Spinner<Integer> bed = new Spinner<>(1, 4, 1);

		java.util.function.Consumer<Integer> applyBedBounds = (Integer rm) -> {
			int max = bedsInRoom(rm);
			int cur = Math.min(bed.getValue(), max);
			bed.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, max, cur));
		};
		room.valueProperty().addListener((o, oldV, newV) -> applyBedBounds.accept(newV));
		applyBedBounds.accept(room.getValue());

		iso.selectedProperty().addListener((o, was, isNow) -> {
			if (isNow) {
				room.getValueFactory().setValue(1);
				bed.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 1, 1));
			} else {
				if (room.getValue() == 1)
					room.getValueFactory().setValue(2);
				applyBedBounds.accept(room.getValue());
			}
		});

		GridPane gp = formGrid(new Label("Manager ID:"), managerId, new Label("Assigned Patient ID:"), tfAutoId,
				new Label("Full name:"), name, new Label("Gender:"), gender, new Label("Isolation:"), iso,
				new Label("Ward:"), ward, new Label("Room (1-6):"), room, new Label("Bed (1-4):"), bed);
		d.getDialogPane().setContent(gp);
		d.setResultConverter(btn -> btn == ButtonType.OK);

		d.showAndWait().ifPresent(ok -> {
			if (!ok)
				return;
			if (name.getText().trim().isEmpty()) {
				showError("Admit Patient", "Full name is required.");
				return;
			}

			int rm = room.getValue();
			int bd = bed.getValue();

			if (bd > bedsInRoom(rm)) {
				showError("Admit Patient", "Room " + rm + " only has " + bedsInRoom(rm) + " bed(s).");
				return;
			}
			if (iso.isSelected() && rm != 1) {
				showError("Admit Patient", "Isolation patients must be placed in Room 1 (single-bed).");
				return;
			}
			if (!iso.isSelected() && rm == 1) {
				showError("Admit Patient", "Room 1 is reserved for isolation patients only.");
				return;
			}

			if (iso.isSelected() && bd != 1) {
				showError("Admit Patient", "Isolation rooms have only Bed 1.");
				return;
			}

			try {
				String newPid = tfAutoId.getText().trim();
				svc.admitPatient(managerId.getText().trim(), newPid, name.getText().trim(), gender.getValue(),
						iso.isSelected(), ward.getValue(), rm, bd);
				setStatus("Admitted " + newPid + " (" + name.getText().trim() + ") to " + ward.getValue() + "-R" + rm
						+ "-B" + bd);
				refreshBeds();
			} catch (Exception ex) {
				showError("Admit failed", ex.getMessage());
			}
		});
	}

	private void promptMovePatient() {
		Dialog<Boolean> d = new Dialog<>();
		d.setTitle("Move Patient");
		d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		TextField nurseId = new TextField();
		ComboBox<DayOfWeek> day = new ComboBox<>();
		day.getItems().addAll(DayOfWeek.values());
		day.getSelectionModel().select(DayOfWeek.MONDAY);
		Spinner<Integer> hour = new Spinner<>(0, 23, 10);

		ComboBox<String> fromWard = new ComboBox<>();
		fromWard.getItems().addAll("A", "B");
		fromWard.getSelectionModel().select("A");
		Spinner<Integer> fromRoom = new Spinner<>(1, 6, 1);
		Spinner<Integer> fromBed = new Spinner<>(1, 4, 1);

		ComboBox<String> toWard = new ComboBox<>();
		toWard.getItems().addAll("A", "B");
		toWard.getSelectionModel().select("A");
		Spinner<Integer> toRoom = new Spinner<>(1, 6, 1);
		Spinner<Integer> toBed = new Spinner<>(1, 4, 1);

		java.util.function.Consumer<Spinner<Integer>> syncFrom = spRoom -> {
			int max = bedsInRoom(spRoom.getValue());
			int cur = Math.min(fromBed.getValue(), max);
			fromBed.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, max, cur));
		};
		java.util.function.Consumer<Spinner<Integer>> syncTo = spRoom -> {
			int max = bedsInRoom(spRoom.getValue());
			int cur = Math.min(toBed.getValue(), max);
			toBed.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, max, cur));
		};
		fromRoom.valueProperty().addListener((o, ov, nv) -> syncFrom.accept(fromRoom));
		toRoom.valueProperty().addListener((o, ov, nv) -> syncTo.accept(toRoom));
		syncFrom.accept(fromRoom);
		syncTo.accept(toRoom);

		GridPane gp = formGrid(new Label("Nurse ID (auth):"), nurseId, new Label("Day:"), day, new Label("Hour:"), hour,
				new Label("From Ward:"), fromWard, new Label("From Room:"), fromRoom, new Label("From Bed:"), fromBed,
				new Label("To Ward:"), toWard, new Label("To Room:"), toRoom, new Label("To Bed:"), toBed);
		d.getDialogPane().setContent(gp);
		d.setResultConverter(btn -> btn == ButtonType.OK);

		d.showAndWait().ifPresent(ok -> {
			if (!ok)
				return;

			int targetRoom = toRoom.getValue();
			int targetBed = toBed.getValue();

			if (targetBed > bedsInRoom(targetRoom)) {
				showError("Move Patient", "Room " + targetRoom + " only has " + bedsInRoom(targetRoom) + " bed(s).");
				return;
			}

			try {
				String pid = svc.patientIdInBed(fromWard.getValue(), fromRoom.getValue(), fromBed.getValue());
				if (pid == null) {
					showError("Move Patient", "Source bed is vacant.");
					return;
				}

				boolean requiresIso = false;
				try (java.sql.Connection c = rmit.s4134401.carehome.util.DB.get();
						java.sql.PreparedStatement ps = c
								.prepareStatement("SELECT isolation FROM patients WHERE id=?")) {
					ps.setString(1, pid);
					try (java.sql.ResultSet rs = ps.executeQuery()) {
						if (rs.next())
							requiresIso = rs.getInt(1) == 1;
					}
				}

				if (requiresIso && targetRoom != 1) {
					showError("Move Patient", "This resident requires isolation → move only to Room 1 (single-bed).");
					return;
				}
				if (!requiresIso && targetRoom == 1) {
					showError("Move Patient", "Room 1 is reserved for isolation patients only.");
					return;
				}
				if (requiresIso && targetBed != 1) {
					showError("Move Patient", "Isolation rooms have only Bed 1.");
					return;
				}

				svc.movePatient(nurseId.getText().trim(), day.getValue(), java.time.LocalTime.of(hour.getValue(), 0),
						fromWard.getValue(), fromRoom.getValue(), fromBed.getValue(), toWard.getValue(), targetRoom,
						targetBed);
				setStatus("Moved " + pid + " to " + toWard.getValue() + "-R" + targetRoom + "-B" + targetBed);
				refreshBeds();
			} catch (Exception ex) {
				showError("Move failed", ex.getMessage());
			}
		});
	}

	private static void bindBedToRoom(Spinner<Integer> room, Spinner<Integer> bed) {
		java.util.function.Consumer<Integer> apply = r -> {
			int max = bedsInRoom(r);
			int cur = Math.min(bed.getValue(), max);
			bed.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, max, cur));
		};
		room.valueProperty().addListener((o, ov, nv) -> apply.accept(nv));
		apply.accept(room.getValue());
	}

	private void showLongInfo(String title, String msg) {
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

	private void promptResidentDetails() {
		Dialog<Boolean> d = new Dialog<>();
		d.setTitle("Resident Details");
		d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		ComboBox<String> ward = new ComboBox<>();
		ward.getItems().addAll("A", "B");
		ward.getSelectionModel().select("A");
		Spinner<Integer> room = new Spinner<>(1, 6, 1);
		Spinner<Integer> bed = new Spinner<>(1, 4, 1);

		GridPane gp = formGrid(new Label("Ward:"), ward, new Label("Room (1-6):"), room, new Label("Bed (1-4):"), bed);
		d.getDialogPane().setContent(gp);
		d.setResultConverter(btn -> btn == ButtonType.OK);

		d.showAndWait().ifPresent(ok -> {
			if (!ok)
				return;
			try {
				String pid = svc.patientIdInBed(ward.getValue(), room.getValue(), bed.getValue());
				if (pid == null) {
					info("Resident Details", "Bed is vacant.");
					return;
				}

				String fullName = null, gender = null;
				boolean iso = false;
				try (java.sql.Connection c = rmit.s4134401.carehome.util.DB.get();
						java.sql.PreparedStatement ps = c
								.prepareStatement("SELECT full_name, gender, isolation FROM patients WHERE id=?")) {
					ps.setString(1, pid);
					try (java.sql.ResultSet rs = ps.executeQuery()) {
						if (rs.next()) {
							fullName = rs.getString("full_name");
							gender = rs.getString("gender");
							iso = rs.getInt("isolation") == 1;
						}
					}
				}

				java.util.List<Prescription> rxs = svc.loadPrescriptionsForPatient(pid);
				java.util.List<MedicationAdministration> admins = svc.administrationsForPatient(pid);

				String rxText = rxs.isEmpty() ? "(no prescriptions)"
						: rxs.stream().map(Prescription::toString).reduce((a, b) -> a + "\n" + b).get();
				String adText = admins.isEmpty() ? "(no administrations)"
						: admins.stream().map(Object::toString).reduce((a, b) -> a + "\n" + b).get();

				String header = (fullName == null || fullName.isBlank()) ? "Patient ID: " + pid
						: fullName + "  (" + pid + ")";

				String msg = header + "\n" + (gender == null ? "" : gender + (iso ? " (isolation)" : "") + "\n")
						+ ward.getValue() + "-R" + room.getValue() + "-B" + bed.getValue() + "\n\n" + "Prescriptions:\n"
						+ rxText + "\n\n" + "Administrations:\n" + adText;

				showLongInfo("Resident Details", msg);
			} catch (Exception ex) {
				showError("Resident Details", ex.getMessage());
			}
		});
	}

	private void promptDischargePatient() {
		Dialog<Boolean> d = new Dialog<>();
		d.setTitle("Discharge Patient");
		d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		ComboBox<String> ward = new ComboBox<>();
		ward.getItems().addAll("A", "B");
		ward.getSelectionModel().select("A");
		Spinner<Integer> room = new Spinner<>(1, 6, 1);
		Spinner<Integer> bed = new Spinner<>(1, 4, 1);

		GridPane gp = formGrid(new Label("Ward:"), ward, new Label("Room (1-6):"), room, new Label("Bed (1-4):"), bed);
		d.getDialogPane().setContent(gp);
		d.setResultConverter(btn -> btn == ButtonType.OK);

		d.showAndWait().ifPresent(ok -> {
			if (!ok)
				return;
			try (java.sql.Connection c = rmit.s4134401.carehome.util.DB.get()) {
				String findSql = "SELECT p.id AS pid " + "FROM patients p " + "JOIN beds b ON p.bed_id=b.id "
						+ "WHERE b.ward=? AND b.room=? AND b.bed_num=? " + "LIMIT 1";
				try (java.sql.PreparedStatement ps = c.prepareStatement(findSql)) {
					ps.setString(1, ward.getValue());
					ps.setInt(2, room.getValue());
					ps.setInt(3, bed.getValue());
					try (java.sql.ResultSet rs = ps.executeQuery()) {
						if (!rs.next()) {
							info("Discharge", "Selected bed is vacant.");
							return;
						}
						String pid = rs.getString("pid");

						try (java.sql.PreparedStatement upd = c
								.prepareStatement("UPDATE patients SET bed_id=NULL WHERE id=?")) {
							upd.setString(1, pid);
							upd.executeUpdate();
						}
						setStatus("Discharged " + pid + " from " + ward.getValue() + "-R" + room.getValue() + "-B"
								+ bed.getValue());
						archivePatientToFile(pid);
						audit("DISCHARGE", "pid=" + pid + " from " + ward.getValue() + "-R" + room.getValue() + "-B"
								+ bed.getValue());
						refreshBeds();
					}
				}
			} catch (Exception ex) {
				showError("Discharge failed", ex.getMessage());
			}
		});
	}

	private void promptAddPrescription() {
		Dialog<Boolean> d = new Dialog<>();
		d.setTitle("Add Prescription");
		d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
		TextField doctorId = new TextField();
		ComboBox<DayOfWeek> day = new ComboBox<>();
		day.getItems().addAll(DayOfWeek.values());
		day.getSelectionModel().select(DayOfWeek.MONDAY);
		ComboBox<String> ward = new ComboBox<>();
		ward.getItems().addAll("A", "B");
		ward.getSelectionModel().select("A");
		Spinner<Integer> room = new Spinner<>(1, 6, 1);
		Spinner<Integer> bed = new Spinner<>(1, 4, 1);
		TextField med = new TextField();
		TextField dose = new TextField();
		TextField times = new TextField("08:00, 14:00");
		GridPane gp = formGrid(new Label("Doctor ID (auth):"), doctorId, new Label("Day:"), day, new Label("Ward:"),
				ward, new Label("Room (1-6):"), room, new Label("Bed (1-4):"), bed, new Label("Medicine:"), med,
				new Label("Dose:"), dose, new Label("Times:"), times);
		d.getDialogPane().setContent(gp);
		d.setResultConverter(btn -> btn == ButtonType.OK);
		d.showAndWait().ifPresent(ok -> {
			if (!ok)
				return;
			try {
				String pid = svc.patientIdInBed(ward.getValue(), room.getValue(), bed.getValue());
				if (pid == null) {
					showError("Add Prescription", "Chosen bed is vacant");
					return;
				}
				if (!doctorExists(doctorId.getText())) {
					showError("Add Prescription", "Doctor '" + doctorId.getText() + "' does not exist.");
					return;
				}
				if (!doctorOnDuty(doctorId.getText(), day.getValue(), 60)) {
					showError("Add Prescription", "Doctor '" + doctorId.getText().trim()
							+ "' is not rostered for at least 60 minutes on " + day.getValue() + ".");
					return;
				}
				svc.doctorAddPrescription(doctorId.getText().trim(), pid, day.getValue(), med.getText().trim(),
						dose.getText().trim(), times.getText().trim());
				setStatus("RX added for " + pid);
			} catch (Exception ex) {
				showError("Add Prescription", ex.getMessage());
			}
		});
	}

	private void promptAdministerMedication() {
		Dialog<Boolean> d = new Dialog<>();
		d.setTitle("Administer Medication");
		d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
		TextField nurseId = new TextField();
		ComboBox<DayOfWeek> day = new ComboBox<>();
		day.getItems().addAll(DayOfWeek.values());
		day.getSelectionModel().select(DayOfWeek.MONDAY);
		Spinner<Integer> hour = new Spinner<>(0, 23, 10);
		ComboBox<String> ward = new ComboBox<>();
		ward.getItems().addAll("A", "B");
		ward.getSelectionModel().select("A");
		Spinner<Integer> room = new Spinner<>(1, 6, 1);
		Spinner<Integer> bed = new Spinner<>(1, 4, 1);
		TextField med = new TextField();
		TextField dose = new TextField();
		GridPane gp = formGrid(new Label("Nurse ID (auth):"), nurseId, new Label("Day:"), day, new Label("Hour:"), hour,
				new Label("Ward:"), ward, new Label("Room (1-6):"), room, new Label("Bed (1-4):"), bed,
				new Label("Medicine:"), med, new Label("Dose:"), dose);
		d.getDialogPane().setContent(gp);
		d.setResultConverter(btn -> btn == ButtonType.OK);
		d.showAndWait().ifPresent(ok -> {
			if (!ok)
				return;
			try {
				String pid = svc.patientIdInBed(ward.getValue(), room.getValue(), bed.getValue());
				if (pid == null) {
					showError("Administer", "Chosen bed is vacant");
					return;
				}
				if (!nurseExists(nurseId.getText())) {
					showError("Administer Medication", "Nurse '" + nurseId.getText() + "' does not exist.");
					return;
				}
				svc.administerMedication(nurseId.getText().trim(), day.getValue(), LocalTime.of(hour.getValue(), 0),
						pid, med.getText().trim(), dose.getText().trim());
				setStatus("Medication administered");
				refreshBeds();
			} catch (Exception ex) {
				showError("Administer Medication", ex.getMessage());
			}
		});
	}

	private void promptUpdateAdministrationDose() {
		Dialog<Boolean> d = new Dialog<>();
		d.setTitle("Update Administration");
		d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		TextField nurseId = new TextField();
		ComboBox<DayOfWeek> day = new ComboBox<>();
		day.getItems().addAll(DayOfWeek.values());
		day.getSelectionModel().select(DayOfWeek.MONDAY);

		Spinner<Integer> hour = new Spinner<>(0, 23, 10);

		ComboBox<String> ward = new ComboBox<>();
		ward.getItems().addAll("A", "B");
		ward.getSelectionModel().select("A");

		Spinner<Integer> room = new Spinner<>(1, 6, 1);
		Spinner<Integer> bed = new Spinner<>(1, 4, 1);

		TextField med = new TextField();
		TextField newDose = new TextField();

		GridPane gp = formGrid(new Label("Nurse ID (auth):"), nurseId, new Label("Day:"), day, new Label("Hour:"), hour,
				new Label("Ward:"), ward, new Label("Room (1-6):"), room, new Label("Bed (1-4):"), bed,
				new Label("Medicine:"), med, new Label("New Dose:"), newDose);
		d.getDialogPane().setContent(gp);
		d.setResultConverter(btn -> btn == ButtonType.OK);

		d.showAndWait().ifPresent(ok -> {
			if (!ok)
				return;
			try {
				String pid = svc.patientIdInBed(ward.getValue(), room.getValue(), bed.getValue());
				if (pid == null) {
					showError("Update Administration", "Chosen bed is vacant");
					return;
				}
				if (!nurseExists(nurseId.getText())) {
					showError("Update Administration", "Nurse '" + nurseId.getText() + "' does not exist.");
					return;
				}

				svc.updateAdministrationDose(nurseId.getText().trim(), day.getValue(), LocalTime.of(hour.getValue(), 0),
						pid, med.getText().trim(), newDose.getText().trim(), true);

				info("Update Administration", "Dose updated for " + pid);
				refreshBeds();
			} catch (Exception ex) {
				showError("Update Administration", ex.getMessage());
			}
		});
	}

	private void promptSetDoctorMinutes() {
		Dialog<Quad<String, String, DayOfWeek, Integer>> d = new Dialog<>();
		d.setTitle("Set Doctor Minutes");
		d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		TextField managerId = new TextField(currentUserId);
		managerId.setEditable(false);
		TextField doctorId = new TextField();
		doctorId.setPromptText("e.g., d1");
		ComboBox<DayOfWeek> day = new ComboBox<>();
		day.getItems().addAll(DayOfWeek.values());
		day.getSelectionModel().select(DayOfWeek.MONDAY);
		TextField mins = new TextField("60");

		GridPane gp = formGrid(new Label("Manager ID (auth):"), managerId, new Label("Doctor ID:"), doctorId,
				new Label("Day:"), day, new Label("Minutes (≥60):"), mins);
		d.getDialogPane().setContent(gp);
		d.setResultConverter(btn -> {
			if (btn != ButtonType.OK)
				return null;
			try {
				return new Quad<>(managerId.getText().trim(), doctorId.getText().trim(), day.getValue(),
						Integer.parseInt(mins.getText().trim()));
			} catch (NumberFormatException nfe) {
				showError("Invalid minutes", "Please enter a number");
				return null;
			}
		});

		d.showAndWait().ifPresent(t -> {
			if (t == null)
				return;
			try {
				if (!doctorExists(t.b)) {
					showError("Set minutes failed", "Doctor '" + t.b + "' does not exist.");
					return;
				}
				svc.setDoctorMinutes(t.a, t.b, t.c, t.d);
				setStatus("Doctor minutes set for " + t.b + " on " + t.c + " = " + t.d);
			} catch (Exception ex) {
				showError("Set minutes failed", ex.getMessage());
			}
		});
	}

	private void promptRemoveDoctorMinutes() {
		Dialog<Quad<String, DayOfWeek, Boolean, Boolean>> d = new Dialog<>();
		d.setTitle("Remove Doctor Minutes");
		d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		TextField doctorId = new TextField();
		doctorId.setPromptText("e.g., d1");

		ComboBox<DayOfWeek> day = new ComboBox<>();
		day.getItems().addAll(DayOfWeek.values());
		day.getSelectionModel().select(DayOfWeek.MONDAY);

		GridPane gp = formGrid(new Label("Doctor ID:"), doctorId, new Label("Day:"), day);
		d.getDialogPane().setContent(gp);
		d.setResultConverter(
				btn -> btn == ButtonType.OK ? new Quad<>(doctorId.getText().trim(), day.getValue(), null, null) : null);

		d.showAndWait().ifPresent(t -> {
			if (t == null)
				return;
			try (var c = rmit.s4134401.carehome.util.DB.get();
					var ps = c.prepareStatement("DELETE FROM doctor_minutes WHERE doctor_id=? AND day=?")) {
				ps.setString(1, t.a);
				ps.setString(2, t.b.name());
				int rows = ps.executeUpdate();
				if (rows > 0)
					info("Remove Doctor Minutes", "Removed entry for " + t.a + " on " + t.b);
				else
					info("Remove Doctor Minutes", "No entry found for that doctor/day.");
				setStatus("Doctor minutes removed");
			} catch (Exception ex) {
				showError("Remove Doctor Minutes", ex.getMessage());
			}
		});
	}

	private void promptAssignShift() {
		Dialog<Quad<String, String, DayOfWeek, Boolean>> d = new Dialog<>();
		d.setTitle("Assign Nurse Shift");
		d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
		TextField managerId = new TextField(currentUserId);
		managerId.setEditable(false);
		TextField nurseId = new TextField();
		ComboBox<DayOfWeek> day = new ComboBox<>();
		day.getItems().addAll(DayOfWeek.values());
		day.getSelectionModel().select(DayOfWeek.MONDAY);
		ComboBox<String> shift = new ComboBox<>();
		shift.getItems().addAll("A (08-16)", "B (14-22)");
		shift.getSelectionModel().select(0);
		GridPane gp = formGrid(new Label("Manager ID (auth):"), managerId, new Label("Nurse ID:"), nurseId,
				new Label("Day:"), day, new Label("Shift:"), shift);
		d.getDialogPane().setContent(gp);
		d.setResultConverter(
				btn -> btn == ButtonType.OK
						? new Quad<>(managerId.getText(), nurseId.getText(), day.getValue(),
								shift.getSelectionModel().getSelectedIndex() == 0)
						: null);
		d.showAndWait().ifPresent(t -> {
			try {
				if (!nurseExists(t.b)) {
					showError("Assign shift failed", "Nurse '" + t.b + "' does not exist.");
					return;
				}
				svc.assignNurseShift(t.a, t.b, t.c, t.d);
				setStatus("Shift assigned");
			} catch (Exception ex) {
				showError("Assign shift failed", ex.getMessage());
			}
		});
	}

	private void promptRemoveShift() {
		Dialog<Quad<String, String, DayOfWeek, Boolean>> d = new Dialog<>();
		d.setTitle("Remove Nurse Shift");
		d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
		TextField managerId = new TextField(currentUserId);
		managerId.setEditable(false);
		TextField nurseId = new TextField();
		ComboBox<DayOfWeek> day = new ComboBox<>();
		day.getItems().addAll(DayOfWeek.values());
		day.getSelectionModel().select(DayOfWeek.MONDAY);
		ComboBox<String> shift = new ComboBox<>();
		shift.getItems().addAll("A (08-16)", "B (14-22)");
		shift.getSelectionModel().select(0);
		GridPane gp = formGrid(new Label("Manager ID (auth):"), managerId, new Label("Nurse ID:"), nurseId,
				new Label("Day:"), day, new Label("Shift:"), shift);
		d.getDialogPane().setContent(gp);
		d.setResultConverter(
				btn -> btn == ButtonType.OK
						? new Quad<>(managerId.getText(), nurseId.getText(), day.getValue(),
								shift.getSelectionModel().getSelectedIndex() == 0)
						: null);
		d.showAndWait().ifPresent(t -> {
			try {
				svc.removeNurseShift(t.a, t.b, t.c, t.d);
				setStatus("Shift removed");
			} catch (Exception ex) {
				showError("Remove shift failed", ex.getMessage());
			}
		});
	}

	private Node buildBedPane() {
		wardsRoot.setPadding(new Insets(16));
		wardsRoot.setAlignment(Pos.TOP_CENTER);
		wardsRoot.setFillHeight(true);

		HBox.setHgrow(wardsRoot, Priority.ALWAYS);

		ScrollPane scroller = new ScrollPane(wardsRoot);
		scroller.setFitToWidth(true);
		scroller.setFitToHeight(true);
		scroller.setStyle("-fx-background:#fafafa;");
		return scroller;
	}

	private Node buildWardColumn(String wardName, java.util.List<BedCell> allCells) {
		VBox column = new VBox(12);
		column.setFillWidth(true);
		column.setPrefWidth(460);

		Label title = new Label("Ward " + wardName);
		title.setStyle("""
				-fx-font-size: 18px;
				-fx-font-weight: bold;
				-fx-text-fill: #2b2b2b;
				""");

		GridPane roomsGrid = new GridPane();
		roomsGrid.setHgap(12);
		roomsGrid.setVgap(12);

		for (int room = 1; room <= 6; room++) {
			Node roomCard = buildRoomCard(wardName, room, allCells);
			int r = (room - 1) / 2;
			int c = (room - 1) % 2;
			roomsGrid.add(roomCard, c, r);
		}

		column.getChildren().addAll(title, roomsGrid);
		VBox.setVgrow(roomsGrid, Priority.ALWAYS);
		return column;
	}

	private Node buildRoomCard(String ward, int room, java.util.List<BedCell> allCells) {
		VBox card = new VBox(8);
		card.setPadding(new Insets(10));
		card.setStyle("""
				-fx-background-color: linear-gradient(#ffffff,#f7f8fb);
				-fx-border-color: #dcdfe6;
				-fx-border-radius: 10;
				-fx-background-radius: 10;
				-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 8,0,0,2);
				""");
		card.setPrefWidth(220);

		Label header = new Label("Room " + room);
		header.setStyle("""
				-fx-font-size: 14px;
				-fx-font-weight: bold;
				-fx-text-fill: #333;
				""");

		FlowPane bedFlow = new FlowPane();
		bedFlow.setHgap(8);
		bedFlow.setVgap(8);
		bedFlow.setPrefWrapLength(220);

		java.util.List<BedCell> beds = new java.util.ArrayList<>();
		for (BedCell bc : allCells) {
			if (ward.equals(bc.ward) && room == bc.room)
				beds.add(bc);
		}

		beds.sort(java.util.Comparator.comparingInt(b -> b.bedNum));

		for (BedCell bc : beds) {
			Button btn = new Button(bc.cellLabel());
			btn.setPrefSize(100, 52);
			styleBedButtonDB(btn, bc);
			btn.setOnAction(e -> onBedClickedDB(bc));
			bedFlow.getChildren().add(btn);
		}

		card.getChildren().addAll(header, bedFlow);
		return card;
	}

	private HBox buildStatusBar() {
		HBox hb = new HBox(20, userBadge, new Region(), status);
		HBox.setHgrow(hb.getChildren().get(1), Priority.ALWAYS);
		hb.setPadding(new Insets(6, 10, 6, 10));
		hb.setAlignment(Pos.CENTER_LEFT);
		hb.setStyle("-fx-background-color: -fx-control-inner-background; -fx-border-color: #ddd;");
		return hb;
	}

	private void refreshBeds() {
		wardsRoot.getChildren().clear();

		java.util.List<BedCell> cells = loadBedCellsFromDB();

		Node wardA = buildWardColumn("A", cells);
		Node wardB = buildWardColumn("B", cells);

		wardsRoot.getChildren().addAll(wardA, wardB);
		HBox.setHgrow(wardA, Priority.ALWAYS);
		HBox.setHgrow(wardB, Priority.ALWAYS);

		setStatus("Beds: " + cells.size());
	}

	private boolean nurseExists(String id) {
		String sql = "SELECT 1 FROM staff WHERE id=? AND role='NURSE' LIMIT 1";
		try (java.sql.Connection c = rmit.s4134401.carehome.util.DB.get();
				java.sql.PreparedStatement ps = c.prepareStatement(sql)) {
			ps.setString(1, id.trim());
			try (java.sql.ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
		} catch (Exception e) {
			showError("DB Error", e.getMessage());
			return false;
		}
	}

	private boolean doctorExists(String id) {
		String sql = "SELECT 1 FROM staff WHERE id=? AND role='DOCTOR' LIMIT 1";
		try (java.sql.Connection c = rmit.s4134401.carehome.util.DB.get();
				java.sql.PreparedStatement ps = c.prepareStatement(sql)) {
			ps.setString(1, id.trim());
			try (java.sql.ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
		} catch (Exception e) {
			showError("DB Error", e.getMessage());
			return false;
		}
	}

	private java.util.List<String> loadStaffFromDB() {
		java.util.List<String> rows = new java.util.ArrayList<>();
		String sql = "SELECT id, name, role FROM staff ORDER BY role, id";
		try (java.sql.Connection c = rmit.s4134401.carehome.util.DB.get();
				java.sql.Statement st = c.createStatement();
				java.sql.ResultSet rs = st.executeQuery(sql)) {
			while (rs.next()) {
				rows.add(String.format("%-6s  %-6s  %s", rs.getString("role"), rs.getString("id"),
						rs.getString("name")));
			}
		} catch (Exception e) {
			showError("DB Error", e.getMessage());
		}
		return rows;
	}

	private void showStaffList() {
		java.util.List<String> lines = loadStaffFromDB();
		String body = lines.isEmpty() ? "(no staff)" : String.join("\n", lines);
		showLongInfo("Staff (role  id  name)", body);
	}

	private void onBedClickedDB(BedCell b) {
		if (b.isVacant()) {
			info("Bed", b.ward + "-R" + b.room + "-B" + b.bedNum + "\n[vacant]");
			return;
		}
		String name = null;
		try (java.sql.Connection c = rmit.s4134401.carehome.util.DB.get();
				java.sql.PreparedStatement ps = c.prepareStatement("SELECT * FROM patients WHERE id=? LIMIT 1")) {
			ps.setString(1, b.patientId);
			try (java.sql.ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					name = safeGetString(rs, "full_name");
				}
			}
		} catch (Exception ignore) {
		}

		String nameLine = (name == null || name.isBlank()) ? b.patientId + " — (no name on file)"
				: b.patientId + " — " + name;

		String genderLine = (b.gender == null) ? "(unknown)" : b.gender.toString();
		String locLine = b.ward + "-R" + b.room + "-B" + b.bedNum;

		info("Resident", nameLine + "\n" + genderLine + (b.isolation ? " (isolation)" : "") + "\n" + locLine);
	}

	private void styleBedButtonDB(Button btn, BedCell b) {
		if (b.isVacant()) {
			btn.setStyle(
					"-fx-background-color: linear-gradient(#e9ffe9,#c6f3c6); -fx-border-color:#7ecb7e; -fx-border-radius:8; -fx-background-radius:8;");
			return;
		}
		String baseColor = (b.gender == Gender.M) ? "-fx-background-color: linear-gradient(#e6f0ff,#cfe0ff);"
				: "-fx-background-color: linear-gradient(#ffe6f0,#ffd0df);";
		String border = b.isolation ? "-fx-border-color: #f39c12; -fx-border-width:2;" : "-fx-border-color: #b3b3b3;";
		btn.setStyle(baseColor + " -fx-background-radius:8; -fx-border-radius:8; " + border);
		btn.setTextFill(javafx.scene.paint.Color.web("#222"));
	}

	private GridPane formGrid(javafx.scene.Node... nodes) {
		GridPane gp = new GridPane();
		gp.setHgap(10);
		gp.setVgap(10);
		gp.setPadding(new Insets(12));
		for (int i = 0; i < nodes.length; i += 2) {
			gp.add(nodes[i], 0, i / 2);
			if (i + 1 < nodes.length)
				gp.add(nodes[i + 1], 1, i / 2);
		}
		ColumnConstraints c0 = new ColumnConstraints();
		c0.setPercentWidth(40);
		ColumnConstraints c1 = new ColumnConstraints();
		c1.setPercentWidth(60);
		gp.getColumnConstraints().addAll(c0, c1);
		return gp;
	}

	private void showError(String title, String msg) {
		Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
		a.setTitle(title);
		a.showAndWait();
		setStatus("Error: " + msg);
	}

	private void info(String title, String msg) {
		Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
		a.setTitle(title);
		a.showAndWait();
		setStatus(msg);
	}

	private void setStatus(String s) {
		status.setText(s);
		audit("STATUS", s);
	}

	private static final class Triple<A, B, C> {
		final A a;
		final B b;
		final C c;

		Triple(A a, B b, C c) {
			this.a = a;
			this.b = b;
			this.c = c;
		}
	}

	private static final class Quad<A, B, C, D> {
		final A a;
		final B b;
		final C c;
		final D d;

		Quad(A a, B b, C c, D d) {
			this.a = a;
			this.b = b;
			this.c = c;
			this.d = d;
		}
	}

	private void ensureAuditTable() {
		String create = """
				    CREATE TABLE IF NOT EXISTS audit(
				      id INTEGER PRIMARY KEY AUTOINCREMENT,
				      staff_id TEXT,
				      action TEXT NOT NULL,
				      details TEXT
				    )
				""";
		try (var c = rmit.s4134401.carehome.util.DB.get(); var st = c.createStatement()) {
			st.execute(create);

			boolean hasTs = false;
			try (var rs = st.executeQuery("PRAGMA table_info(audit)")) {
				while (rs.next()) {
					if ("ts".equalsIgnoreCase(rs.getString("name"))) {
						hasTs = true;
						break;
					}
				}
			}
			if (!hasTs) {
				st.execute("ALTER TABLE audit ADD COLUMN ts TEXT");
			}
			st.execute("UPDATE audit SET ts = COALESCE(ts, datetime('now'))");

			boolean hasAction = false, hasType = false;
			try (var rs = st.executeQuery("PRAGMA table_info(audit)")) {
				while (rs.next()) {
					String n = rs.getString("name");
					if ("action".equalsIgnoreCase(n))
						hasAction = true;
					if ("type".equalsIgnoreCase(n))
						hasType = true;
				}
			}
			if (!hasAction) {
				st.execute("ALTER TABLE audit ADD COLUMN action TEXT");
				if (hasType) {
					st.execute("UPDATE audit SET action = COALESCE(action, type)");
				}
			}

		} catch (Exception ignore) {
		}
	}

	private void audit(String action, String details) {
		try (var c = rmit.s4134401.carehome.util.DB.get()) {
			try (var ps = c.prepareStatement("INSERT INTO audit(ts, staff_id, action, details) VALUES(?,?,?,?)")) {
				ps.setString(1, java.time.OffsetDateTime.now().toString());
				ps.setString(2, currentUserId);
				ps.setString(3, action);
				ps.setString(4, details);
				ps.executeUpdate();
				return;
			}
		} catch (Exception e) {
		}
		try (var c2 = rmit.s4134401.carehome.util.DB.get();
				var ps2 = c2.prepareStatement("INSERT INTO audit(staff_id, action, details) VALUES(?,?,?)")) {
			ps2.setString(1, currentUserId);
			ps2.setString(2, action);
			ps2.setString(3, details);
			ps2.executeUpdate();
		} catch (Exception ignore) {
		}
	}

	private void exportAuditToFile() {
		java.nio.file.Path out = java.nio.file.Path.of("audit_latest.txt").toAbsolutePath();

		try (var c = rmit.s4134401.carehome.util.DB.get(); var st = c.createStatement()) {

			java.util.Set<String> cols = new java.util.HashSet<>();
			try (var rs = st.executeQuery("PRAGMA table_info(audit)")) {
				while (rs.next())
					cols.add(rs.getString("name").toLowerCase());
			}

			String tsExpr = cols.contains("ts") ? "ts" : "datetime('now') AS ts";
			String staffExpr = cols.contains("staff_id") ? "staff_id" : "NULL AS staff_id";
			String actionExpr = cols.contains("action") ? "action" : cols.contains("type") ? "type" : "'-' AS action";
			String detailsExpr = cols.contains("details") ? "details"
					: cols.contains("message") ? "message" : "NULL AS details";
			String idOrRowid = cols.contains("id") ? "id" : "rowid";

			String sql = "SELECT " + tsExpr + ", " + staffExpr + ", " + actionExpr + ", " + detailsExpr
					+ " FROM audit ORDER BY " + idOrRowid + " DESC LIMIT 1000";

			try (var rs = st.executeQuery(sql)) {
				StringBuilder sb = new StringBuilder();
				int rows = 0;
				while (rs.next()) {
					rows++;
					sb.append(rs.getString(1)).append(" | ").append(rs.getString(2)).append(" | ")
							.append(rs.getString(3)).append(" | ").append(rs.getString(4)).append('\n');
				}
				java.nio.file.Files.writeString(out, sb.toString(), java.nio.charset.StandardCharsets.UTF_8);
				info("Export Logs", "Saved " + out + " (" + rows + " rows)");
			}
		} catch (Exception e) {
			showError("Export Logs", "Failed to export audit: " + e.getMessage());
		}
	}

	private void savePatientsSnapshot(String filePath) {
		StringBuilder json = new StringBuilder();
		json.append("{\"ts\":\"").append(OffsetDateTime.now()).append("\",");

		json.append("\"patients\":[");
		try (var c = rmit.s4134401.carehome.util.DB.get();
				var st = c.createStatement();
				var rs = st.executeQuery("SELECT id, full_name, gender, isolation, bed_id FROM patients ORDER BY id")) {
			boolean first = true;
			while (rs.next()) {
				if (!first)
					json.append(',');
				first = false;
				json.append('{').append("\"id\":\"").append(rs.getString("id")).append("\",").append("\"full_name\":")
						.append(toJson(rs.getString("full_name"))).append(',').append("\"gender\":")
						.append(toJson(rs.getString("gender"))).append(',').append("\"isolation\":")
						.append(rs.getInt("isolation")).append(',').append("\"bed_id\":")
						.append(rs.getObject("bed_id") == null ? "null" : rs.getInt("bed_id")).append('}');
			}
		} catch (Exception ignore) {
		}
		json.append("],");

		json.append("\"prescriptions\":[");
		try (var c = rmit.s4134401.carehome.util.DB.get();
				var st = c.createStatement();
				var rs = st.executeQuery("SELECT * FROM prescriptions ORDER BY id")) {
			boolean first = true;
			while (rs.next()) {
				if (!first)
					json.append(',');
				first = false;
				json.append('{').append("\"id\":").append(rs.getInt("id")).append(',').append("\"patient_id\":\"")
						.append(rs.getString("patient_id")).append("\",").append("\"doctor_id\":\"")
						.append(rs.getString("doctor_id")).append("\",").append("\"day\":")
						.append(toJson(rs.getString("day"))).append(',').append("\"created_ts\":")
						.append(toJson(rs.getString("created_ts"))).append('}');
			}
		} catch (Exception ignore) {
		}
		json.append("],");

		json.append("\"prescription_lines\":[");
		try (var c = rmit.s4134401.carehome.util.DB.get();
				var st = c.createStatement();
				var rs = st.executeQuery("SELECT * FROM prescription_lines ORDER BY id")) {
			boolean first = true;
			while (rs.next()) {
				if (!first)
					json.append(',');
				first = false;
				json.append('{').append("\"id\":").append(rs.getInt("id")).append(',').append("\"prescription_id\":")
						.append(rs.getInt("prescription_id")).append(',').append("\"medicine\":")
						.append(toJson(rs.getString("medicine"))).append(',').append("\"dose\":")
						.append(toJson(rs.getString("dose"))).append(',').append("\"times\":")
						.append(toJson(rs.getString("times"))).append('}');
			}
		} catch (Exception ignore) {
		}
		json.append("],");

		json.append("\"administrations\":[");
		try (var c = rmit.s4134401.carehome.util.DB.get();
				var st = c.createStatement();
				var rs = st.executeQuery("SELECT * FROM administrations ORDER BY id")) {
			boolean first = true;
			while (rs.next()) {
				if (!first)
					json.append(',');
				first = false;
				json.append('{').append("\"id\":").append(rs.getInt("id")).append(',').append("\"patient_id\":\"")
						.append(rs.getString("patient_id")).append("\",").append("\"medicine\":")
						.append(toJson(rs.getString("medicine"))).append(',').append("\"dose\":")
						.append(toJson(rs.getString("dose"))).append(',').append("\"day\":")
						.append(toJson(rs.getString("day"))).append(',').append("\"time\":")
						.append(toJson(rs.getString("time"))).append(',').append("\"staff_id\":")
						.append(toJson(rs.getString("staff_id"))).append(',').append("\"is_update\":")
						.append(rs.getInt("is_update")).append('}');
			}
		} catch (Exception ignore) {
		}
		json.append("]}");

		try {
			Files.write(Path.of(filePath), json.toString().getBytes(StandardCharsets.UTF_8));
			audit("SNAPSHOT_SAVE", "Saved patient snapshot -> " + filePath);
		} catch (Exception e) {
		}
	}

	private static String toJson(String s) {
		if (s == null)
			return "null";
		return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	private void restorePatientsSnapshot(String filePath) {
		try {
			String json = Files.readString(Path.of(filePath), StandardCharsets.UTF_8);
			int i = json.indexOf("\"patients\":[");
			if (i < 0)
				throw new RuntimeException("patients[] not found");
			int start = i + "\"patients\":[".length();
			int end = json.indexOf("]", start);
			String body = json.substring(start, end);

			try (var c = rmit.s4134401.carehome.util.DB.get()) {
				String[] items = body.split("\\},\\{");
				for (String raw : items) {
					String item = raw.replace("{", "").replace("}", "");
					Map<String, String> kv = new HashMap<>();
					for (String part : item.split(",\"")) {
						String p = part.replaceFirst("^\"", "");
						int colon = p.indexOf("\":");
						if (colon < 0)
							continue;
						String k = p.substring(0, colon);
						String v = p.substring(colon + 2);
						kv.put(k, v);
					}
					String id = stripJson(kv.get("id"));
					String fullName = stripJson(kv.get("full_name"));
					String gender = stripJson(kv.get("gender"));
					int isolation = Integer.parseInt(kv.get("isolation"));
					String bedIdStr = kv.get("bed_id");
					Integer bedId = "null".equals(bedIdStr) ? null : Integer.valueOf(bedIdStr);

					try (var up = c.prepareStatement("""
							    INSERT INTO patients(id, full_name, gender, isolation, bed_id)
							    VALUES(?,?,?,?,?)
							    ON CONFLICT(id) DO UPDATE SET full_name=excluded.full_name,
							                                 gender=excluded.gender,
							                                 isolation=excluded.isolation,
							                                 bed_id=excluded.bed_id
							""")) {
						up.setString(1, id);
						up.setString(2, fullName);
						up.setString(3, gender);
						up.setInt(4, isolation);
						if (bedId == null)
							up.setNull(5, java.sql.Types.INTEGER);
						else
							up.setInt(5, bedId);
						up.executeUpdate();
					}
				}
			}
			audit("SNAPSHOT_RESTORE", "Restored patients from " + filePath);
		} catch (Exception e) {
			showError("Restore Snapshot", e.getMessage());
		}
	}

	private static String stripJson(String v) {
		if (v == null)
			return null;
		v = v.trim();
		if (v.equals("null"))
			return null;
		if (v.startsWith("\"") && v.endsWith("\""))
			return v.substring(1, v.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
		return v;
	}

	public static void main(String[] args) {
		launch(args);
	}
}
