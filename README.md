# **Resident HealthCare System**

Small teaching app to manage a tiny care home — staff, rosters, wards/beds, residents, prescriptions, medication administrations, and an audit log.  
Built using JavaFX for the user interface and SQLite for storage. The main logic is handled through the `CareHomeService` class.

---

## **How to Run (Maven)**

1. Make sure you have **JDK 17 or higher** installed.
2. Open a terminal in the project root folder.
3. Run the following command:


When launched for the first time, the app will:

- Create or patch the database file `carehome.db`.
- Generate the ward and bed layout:
- Ward A and Ward B.
- Rooms 1–6 (Room 1 = 1 bed, Room 2 = 2 beds, Rooms 3–6 = 4 beds each).
- Add demo login accounts if none exist.

**Default accounts:**

| Role | ID | Password |
|------|----|-----------|
| Manager | m1 | m1 |
| Doctor | d1 | d1 |
| Nurse | n1 | n1 |

---

## **System Rules**

- **Nurse Shifts**  
Shift A = 08:00–16:00  
Shift B = 14:00–22:00  
A nurse cannot exceed 8 hours in a single day.

- **Doctor Coverage**  
Each doctor has coverage minutes per day. At least 60 minutes are required to prescribe medication.

- **Isolation**  
Isolation patients can only occupy Room 1 Bed 1.  
Non-isolation patients cannot use Room 1.

- **Resident Movement**  
Only nurses rostered at that day and time can move residents.

- **Prescriptions**  
Only doctors can add prescriptions, and only if they have at least 60 minutes rostered on that day.

- **Medication Administration**  
Only nurses on shift can administer medication.  
Dose updates are recorded as separate audit entries.

- **Audit Logging**  
Every major action (admit, move, prescribe, administer, update, etc.) is logged with timestamp, staff ID, and details.  
The app automatically patches the audit table if an older database is opened.

---

## **Files Created at Runtime**

- `carehome.db` — SQLite database file.  
- `audit_latest.txt` — Exported audit (from *File → Export Logs…*).  
- `roster_week.txt` — Weekly roster summary (from *Schedule → Export Week…*).  
- `archive/` — Folder containing discharge archive text files.  
- `patients_snapshot.json` — Snapshot for restore/import of current patient data.

---

## **Troubleshooting**

- **Export Logs shows “no such column: ts” or “no such column: action”**  
You opened an old database. Launch the app once to let it patch automatically, then export again.  
If you don’t need the old data, delete `carehome.db` and relaunch.

- **Nurse cannot administer or move patients**  
The nurse must be rostered and on shift at that specific time.

- **Doctor cannot add prescriptions**  
Ensure that doctor has ≥ 60 minutes of coverage for that day.

---

## **Running Tests**

Unit tests are written with **JUnit 5** and run without JavaFX.  
They use a temporary SQLite database and the real JDBC repositories to validate the business rules in `CareHomeService`.

**Run tests (Maven):**



