package db;

import model.Friendship;
import java.sql.*;
import java.util.*;

/**
 * friendships テーブルの CRUD
 * MySQL / SQLite 両対応
 *
 * テーブル定義:
 *   friendships (
 *     player_id_a INTEGER NOT NULL,
 *     player_id_b INTEGER NOT NULL,  -- a < b を保証
 *     points      INTEGER NOT NULL DEFAULT 0,
 *     updated_at  DATETIME,
 *     PRIMARY KEY (player_id_a, player_id_b)
 *   )
 */
public class FriendshipDao {

    private final DatabaseManager db = DatabaseManager.getInstance();

    // ── テーブル作成SQL（DatabaseManagerから呼ぶ） ─────────
    public static String createTableSql() {
        boolean mysql = DatabaseConfig.isMySql();
        String  AI    = DatabaseConfig.autoIncrement();
        String  OPT   = DatabaseConfig.tableOptions();
        String  NOW   = mysql ? "NOW()" : "datetime('now','localtime')";
        return String.format("""
            CREATE TABLE IF NOT EXISTS friendships (
                player_id_a INTEGER NOT NULL,
                player_id_b INTEGER NOT NULL,
                points      INTEGER NOT NULL DEFAULT 0,
                updated_at  DATETIME DEFAULT (%s),
                PRIMARY KEY (player_id_a, player_id_b)
            )%s
            """, NOW, OPT);
    }

    // ── upsert（INSERT or UPDATE） ─────────────────────────
    public void upsert(Friendship f) {
        String sql = DatabaseConfig.isMySql()
            ? """
              INSERT INTO friendships (player_id_a, player_id_b, points)
              VALUES (?,?,?)
              ON DUPLICATE KEY UPDATE points=VALUES(points)
              """
            : """
              INSERT OR REPLACE INTO friendships (player_id_a, player_id_b, points)
              VALUES (?,?,?)
              """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setInt(1, f.getPlayerIdA());
            ps.setInt(2, f.getPlayerIdB());
            ps.setInt(3, f.getPoints());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[FriendshipDao] upsert エラー: " + e.getMessage());
        }
    }

    // ── 全件取得 ──────────────────────────────────────────
    public List<Friendship> findAll() {
        List<Friendship> list = new ArrayList<>();
        String sql = "SELECT player_id_a, player_id_b, points FROM friendships";
        try (Statement stmt = db.getConnection().createStatement();
             ResultSet rs   = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Friendship(
                    rs.getInt("player_id_a"),
                    rs.getInt("player_id_b"),
                    rs.getInt("points")));
            }
        } catch (SQLException e) {
            System.err.println("[FriendshipDao] findAll エラー: " + e.getMessage());
        }
        return list;
    }

    // ── 特定選手のペアを取得 ──────────────────────────────
    public List<Friendship> findByPlayer(int playerId) {
        List<Friendship> list = new ArrayList<>();
        String sql = """
            SELECT player_id_a, player_id_b, points
            FROM friendships
            WHERE player_id_a = ? OR player_id_b = ?
            ORDER BY points DESC
            """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setInt(1, playerId);
            ps.setInt(2, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Friendship(
                        rs.getInt("player_id_a"),
                        rs.getInt("player_id_b"),
                        rs.getInt("points")));
                }
            }
        } catch (SQLException e) {
            System.err.println("[FriendshipDao] findByPlayer エラー: " + e.getMessage());
        }
        return list;
    }

    // ── 2選手間を直接取得 ─────────────────────────────────
    public Optional<Friendship> find(int idA, int idB) {
        int a = Math.min(idA, idB), b = Math.max(idA, idB);
        String sql = """
            SELECT player_id_a, player_id_b, points
            FROM friendships
            WHERE player_id_a = ? AND player_id_b = ?
            """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setInt(1, a); ps.setInt(2, b);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new Friendship(
                        rs.getInt("player_id_a"),
                        rs.getInt("player_id_b"),
                        rs.getInt("points")));
                }
            }
        } catch (SQLException e) {
            System.err.println("[FriendshipDao] find エラー: " + e.getMessage());
        }
        return Optional.empty();
    }
}
