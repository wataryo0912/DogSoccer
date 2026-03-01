package db;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 試合結果・スタッツのDB操作クラス
 */
public class MatchDao {

    private final DatabaseManager db = DatabaseManager.getInstance();

    /**
     * 試合結果を保存し、matchIdを返す
     */
    public int insertMatch(int homeClubId, int awayClubId,
                           int homeGoals, int awayGoals) throws SQLException {
        String sql = """
            INSERT INTO matches (home_club_id, away_club_id, home_goals, away_goals)
            VALUES (?, ?, ?, ?)
        """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, homeClubId);
            ps.setInt(2, awayClubId);
            ps.setInt(3, homeGoals);
            ps.setInt(4, awayGoals);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            return rs.next() ? rs.getInt(1) : -1;
        }
    }

    /**
     * 試合スタッツを保存
     */
    public void insertStats(int matchId,
                            int homeSh, int awaySh,
                            int homeShOn, int awayShOn,
                            int homeCor, int awayCor,
                            int homeFoul, int awayFoul,
                            int homePoss) throws SQLException {
        String sql = """
            INSERT INTO match_stats
              (match_id, home_shots, away_shots,
               home_shots_on, away_shots_on,
               home_corners, away_corners,
               home_fouls, away_fouls, home_poss)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setInt(1,  matchId);
            ps.setInt(2,  homeSh);
            ps.setInt(3,  awaySh);
            ps.setInt(4,  homeShOn);
            ps.setInt(5,  awayShOn);
            ps.setInt(6,  homeCor);
            ps.setInt(7,  awayCor);
            ps.setInt(8,  homeFoul);
            ps.setInt(9,  awayFoul);
            ps.setInt(10, homePoss);
            ps.executeUpdate();
        }
    }

    /**
     * 直近N件の試合結果を取得
     */
    public List<String> findRecentResults(int limit) throws SQLException {
        String sql = """
            SELECT m.id, c1.name home_name, c2.name away_name,
                   m.home_goals, m.away_goals, m.played_at
            FROM matches m
            JOIN clubs c1 ON c1.id = m.home_club_id
            JOIN clubs c2 ON c2.id = m.away_club_id
            ORDER BY m.played_at DESC
            LIMIT ?
        """;
        List<String> results = new ArrayList<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                results.add(String.format("[%s]  %s  %d - %d  %s",
                    rs.getString("played_at").substring(0, 16),
                    rs.getString("home_name"),
                    rs.getInt("home_goals"),
                    rs.getInt("away_goals"),
                    rs.getString("away_name")
                ));
            }
        }
        return results;
    }

    /**
     * クラブIDに関わる全試合を取得
     */
    public List<String> findByClubId(int clubId, int limit) throws SQLException {
        String sql = """
            SELECT m.id, c1.name home_name, c2.name away_name,
                   m.home_goals, m.away_goals, m.played_at
            FROM matches m
            JOIN clubs c1 ON c1.id = m.home_club_id
            JOIN clubs c2 ON c2.id = m.away_club_id
            WHERE m.home_club_id = ? OR m.away_club_id = ?
            ORDER BY m.played_at DESC
            LIMIT ?
        """;
        List<String> results = new ArrayList<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setInt(1, clubId);
            ps.setInt(2, clubId);
            ps.setInt(3, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                results.add(String.format("[%s]  %s  %d - %d  %s",
                    rs.getString("played_at").substring(0, 16),
                    rs.getString("home_name"),
                    rs.getInt("home_goals"),
                    rs.getInt("away_goals"),
                    rs.getString("away_name")
                ));
            }
        }
        return results;
    }

    /**
     * 試合総数を返す
     */
    public int countMatches() throws SQLException {
        try (Statement stmt = db.getConnection().createStatement();
             ResultSet rs   = stmt.executeQuery("SELECT COUNT(*) FROM matches")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
}
