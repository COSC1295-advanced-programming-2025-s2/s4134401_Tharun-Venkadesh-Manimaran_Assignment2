package rmit.s4134401.carehome.repo.jdbc;

import rmit.s4134401.carehome.repo.BedRepository;
import rmit.s4134401.carehome.util.DB;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcBedRepository implements BedRepository {

    public Optional<Integer> findBedId(String ward, int room, int bedNum) {
        try (Connection c = DB.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id FROM beds WHERE ward=? AND room=? AND bed_num=?")) {
            ps.setString(1, ward);
            ps.setInt(2, room);
            ps.setInt(3, bedNum);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return Optional.empty();
            return Optional.of(rs.getInt(1));
        } catch (SQLException e) {
            throw new RuntimeException("findBedId failed: " + e.getMessage(), e);
        }
    }

    public List<int[]> listCoords() {
        List<int[]> out = new ArrayList<int[]>();
        try (Connection c = DB.get();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, room, bed_num FROM beds ORDER BY ward, room, bed_num")) {
            while (rs.next()) {
                out.add(new int[]{ rs.getInt("id"), rs.getInt("room"), rs.getInt("bed_num") });
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("listCoords failed: " + e.getMessage(), e);
        }
    }
}
