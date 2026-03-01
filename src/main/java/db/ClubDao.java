package db;

import model.Club;
import java.sql.*;
import java.util.*;

/**
 * クラブのDB操作
 * MySQL / SQLite 両対応（DatabaseConfig で切り替え）
 */
public class ClubDao {

    private final DatabaseManager db = DatabaseManager.getInstance();

    // ── INSERT ───────────────────────────────────────────────
    public int insert(Club club) throws SQLException {
        String sql = """
            INSERT INTO clubs
              (name, color, formation, breed, uniform_image,
               budget, weekly_salary_budget, wins, draws, losses)
            VALUES (?,?,?,?,?,?,?,?,?,?)
        """;
        try (PreparedStatement ps = db.getConnection()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, club);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            return rs.next() ? rs.getInt(1) : -1;
        }
    }

    // ── UPDATE ───────────────────────────────────────────────
    public void update(Club club) throws SQLException {
        String sql = """
            UPDATE clubs
            SET color=?, formation=?, breed=?, uniform_image=?,
                budget=?, weekly_salary_budget=?, wins=?, draws=?, losses=?
            WHERE name=?
        """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, club.getColor());
            ps.setString(2, club.getFormation());
            ps.setString(3, club.getBreed());
            ps.setString(4, club.getUniformImageFile());
            ps.setLong  (5, club.getBudget());
            ps.setLong  (6, club.getWeeklySalaryBudget());
            ps.setInt   (7, club.getWins());
            ps.setInt   (8, club.getDraws());
            ps.setInt   (9, club.getLosses());
            ps.setString(10, club.getName());
            ps.executeUpdate();
        }
    }

    // ── UPSERT ───────────────────────────────────────────────
    public int upsert(Club club) throws SQLException {
        int id = findIdByName(club.getName());
        if (id > 0) { update(club); return id; }
        return insert(club);
    }

    // ── SELECT ───────────────────────────────────────────────
    public List<Club> findAll() throws SQLException {
        List<Club> list = new ArrayList<>();
        try (Statement stmt = db.getConnection().createStatement();
             ResultSet rs   = stmt.executeQuery("SELECT * FROM clubs ORDER BY id")) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public Club findByName(String name) throws SQLException {
        String sql = "SELECT * FROM clubs WHERE name = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? map(rs) : null;
        }
    }

    public int findIdByName(String name) throws SQLException {
        String sql = "SELECT id FROM clubs WHERE name = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt("id") : -1;
        }
    }

    // ── マッピング ───────────────────────────────────────────
    private void bind(PreparedStatement ps, Club c) throws SQLException {
        ps.setString(1, c.getName());
        ps.setString(2, c.getColor());
        ps.setString(3, c.getFormation());
        ps.setString(4, c.getBreed());
        ps.setString(5, c.getUniformImageFile());
        ps.setLong  (6, c.getBudget());
        ps.setLong  (7, c.getWeeklySalaryBudget());
        ps.setInt   (8, c.getWins());
        ps.setInt   (9, c.getDraws());
        ps.setInt   (10, c.getLosses());
    }

    private Club map(ResultSet rs) throws SQLException {
        Club club = new Club(
            rs.getString("name"),
            rs.getLong  ("budget"),
            rs.getLong  ("weekly_salary_budget")
        );
        club.setId              (rs.getInt   ("id"));
        club.setColor           (rs.getString("color"));
        club.setFormation       (rs.getString("formation"));
        club.setBreed           (rs.getString("breed"));
        club.setUniformImageFile(rs.getString("uniform_image"));

        int w = rs.getInt("wins"), d = rs.getInt("draws"), l = rs.getInt("losses");
        for (int i = 0; i < w; i++) club.recordResult(1, 0);
        for (int i = 0; i < d; i++) club.recordResult(0, 0);
        for (int i = 0; i < l; i++) club.recordResult(0, 1);
        return club;
    }
}
