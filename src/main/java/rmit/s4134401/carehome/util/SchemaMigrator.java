package rmit.s4134401.carehome.util;

import java.sql.Connection;
import java.sql.Statement;

public final class SchemaMigrator {
    private SchemaMigrator(){}

    public static void ensure(){
        try {
            Connection c = DB.get();
            Statement s = c.createStatement();

            s.execute("CREATE TABLE IF NOT EXISTS staff(" +
                    "id TEXT PRIMARY KEY, name TEXT NOT NULL, role TEXT NOT NULL, password TEXT NOT NULL DEFAULT '')");

            s.execute("CREATE TABLE IF NOT EXISTS beds(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, ward TEXT NOT NULL, room INTEGER NOT NULL, bed_num INTEGER NOT NULL," +
                    "UNIQUE(ward,room,bed_num))");

            s.execute("CREATE TABLE IF NOT EXISTS patients(" +
                    "id TEXT PRIMARY KEY, full_name TEXT NOT NULL, gender TEXT NOT NULL, isolation INTEGER NOT NULL," +
                    "bed_id INTEGER NULL, FOREIGN KEY(bed_id) REFERENCES beds(id))");

            s.execute("CREATE TABLE IF NOT EXISTS nurse_shifts(" +
                    "nurse_id TEXT NOT NULL, day TEXT NOT NULL, start TEXT NOT NULL, end TEXT NOT NULL," +
                    "PRIMARY KEY(nurse_id,day,start,end))");

            s.execute("CREATE TABLE IF NOT EXISTS doctor_minutes(" +
                    "day TEXT PRIMARY KEY, minutes INTEGER NOT NULL)");

            s.execute("CREATE TABLE IF NOT EXISTS audit(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, when_ts TEXT NOT NULL, staff_id TEXT NOT NULL, type TEXT NOT NULL, details TEXT NOT NULL)");

            s.execute("CREATE TABLE IF NOT EXISTS prescriptions(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, patient_id TEXT NOT NULL, doctor_id TEXT NOT NULL, day TEXT NOT NULL, created_ts TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS rx_lines(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, rx_id INTEGER NOT NULL, medicine TEXT NOT NULL, dose TEXT NOT NULL, times TEXT NOT NULL," +
                    "FOREIGN KEY(rx_id) REFERENCES prescriptions(id))");

            s.execute("CREATE TABLE IF NOT EXISTS administrations(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, patient_id TEXT NOT NULL, medicine TEXT NOT NULL, dose TEXT NOT NULL," +
                    "day TEXT NOT NULL, time TEXT NOT NULL, staff_id TEXT NOT NULL, is_correction INTEGER NOT NULL DEFAULT 0)");

            s.execute("INSERT OR IGNORE INTO beds(id,ward,room,bed_num) " +
                    "SELECT NULL,'A',r,b FROM (SELECT 1 r UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6)," +
                    "(SELECT 1 b UNION SELECT 2 UNION SELECT 3 UNION SELECT 4) WHERE (r IN (1,2) AND b<=1) OR (r IN (3,4) AND b<=2) OR (r=5 AND b<=3) OR (r=6)");
            s.execute("INSERT OR IGNORE INTO beds(id,ward,room,bed_num) " +
                    "SELECT NULL,'B',r,b FROM (SELECT 1 r UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6)," +
                    "(SELECT 1 b UNION SELECT 2 UNION SELECT 3 UNION SELECT 4) WHERE (r IN (1,2) AND b<=1) OR (r IN (3,4) AND b<=2) OR (r=5 AND b<=3) OR (r=6)");

            s.close();
            c.close();
        } catch (Exception e){
            throw new RuntimeException("Schema ensure failed: " + e.getMessage(), e);
        }
    }
}
