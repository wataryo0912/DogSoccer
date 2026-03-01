package db;

import model.WeeklyEvent;
import java.sql.*;
import java.util.*;

/**
 * weekly_events テーブルの CRUD
 * MySQL / SQLite 両対応（DatabaseConfig 経由）
 */
public class WeeklyEventDao {

    private final DatabaseManager db = DatabaseManager.getInstance();

    // ── テーブル作成（DatabaseManagerから呼ぶ） ─────────────────
    public static String createTableSql() {
        boolean mysql = DatabaseConfig.isMySql();
        String  AI    = DatabaseConfig.autoIncrement();
        String  OPT   = DatabaseConfig.tableOptions();
        String  NOW   = mysql ? "NOW()" : "datetime('now','localtime')";
        return String.format("""
            CREATE TABLE IF NOT EXISTS weekly_events (
                id             INTEGER      PRIMARY KEY %s,
                week           INTEGER      NOT NULL,
                season         INTEGER      NOT NULL DEFAULT 1,
                event_type     VARCHAR(30)  NOT NULL,
                event_key      VARCHAR(50)  NOT NULL,
                title          VARCHAR(100) NOT NULL DEFAULT '',
                description    VARCHAR(500) NOT NULL DEFAULT '',
                player_id      INTEGER      REFERENCES players(id) ON DELETE SET NULL,
                effect_type    VARCHAR(20)  NOT NULL DEFAULT 'NONE',
                effect_target  VARCHAR(20)  NOT NULL DEFAULT '',
                effect_value   INTEGER      NOT NULL DEFAULT 0,
                created_at     DATETIME     DEFAULT (%s)
            )%s
        """, AI, NOW, OPT);
    }

    // ── INSERT ───────────────────────────────────────────────────
    public int insert(WeeklyEvent e) throws SQLException {
        String sql = """
            INSERT INTO weekly_events
              (week, season, event_type, event_key, title, description,
               player_id, effect_type, effect_target, effect_value)
            VALUES (?,?,?,?,?,?,?,?,?,?)
        """;
        try (PreparedStatement ps = db.getConnection()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt   (1, e.getWeek());
            ps.setInt   (2, e.getSeason());
            ps.setString(3, e.getEventType().name());
            ps.setString(4, e.getEventKey());
            ps.setString(5, e.getTitle());
            ps.setString(6, e.getDescription());
            if (e.getTargetPlayer() != null && e.getTargetPlayer().getId() > 0)
                ps.setInt(7, e.getTargetPlayer().getId());
            else
                ps.setNull(7, Types.INTEGER);
            ps.setString(8, e.getEffectType().name());
            ps.setString(9, e.getEffectTarget());
            ps.setInt  (10, e.getEffectValue());
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            int newId = rs.next() ? rs.getInt(1) : -1;
            e.setId(newId);
            return newId;
        }
    }

    // ── SELECT ───────────────────────────────────────────────────
    /** シーズン内の全イベントを週順で取得 */
    public List<WeeklyEvent> findBySeason(int season) throws SQLException {
        String sql = """
            SELECT * FROM weekly_events WHERE season=? ORDER BY week ASC
        """;
        List<WeeklyEvent> list = new ArrayList<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setInt(1, season);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    /** 直近 n 件のイベントを取得 */
    public List<WeeklyEvent> findRecent(int n) throws SQLException {
        String sql = """
            SELECT * FROM weekly_events ORDER BY id DESC LIMIT ?
        """;
        List<WeeklyEvent> list = new ArrayList<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setInt(1, n);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    /** 今週のイベントを取得（なければ null） */
    public WeeklyEvent findByWeekAndSeason(int week, int season) throws SQLException {
        String sql = """
            SELECT * FROM weekly_events WHERE week=? AND season=? LIMIT 1
        """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setInt(1, week);
            ps.setInt(2, season);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? map(rs) : null;
        }
    }

    // ── マッピング ───────────────────────────────────────────────
    private WeeklyEvent map(ResultSet rs) throws SQLException {
        WeeklyEvent e = new WeeklyEvent(
            rs.getInt   ("week"),
            rs.getInt   ("season"),
            WeeklyEvent.EventType .valueOf(rs.getString("event_type")),
            rs.getString("event_key"),
            rs.getString("title"),
            rs.getString("description"),
            null,   // targetPlayer は ID だけ保持（後で解決が必要なら別途ロード）
            WeeklyEvent.EffectType.valueOf(rs.getString("effect_type")),
            rs.getString("effect_target"),
            rs.getInt   ("effect_value")
        );
        e.setId(rs.getInt("id"));
        return e;
    }
}
