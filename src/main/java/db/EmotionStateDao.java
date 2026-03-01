package db;

import model.EmotionState;
import model.EmotionState.Mood;
import model.Player;

import java.sql.*;

/**
 * 感情パラメータの DB 永続化
 *
 * テーブル: player_emotions
 *   player_id   INT         PK, FK → players.id
 *   confidence  INT
 *   frustration INT
 *   loyalty     INT
 *   rivalry     INT
 *   mood        VARCHAR(10) 'HAPPY'|'NORMAL'|'DOWN'|'SLUMP'
 *   benched_weeks INT
 *   updated_at  TIMESTAMP   自動更新
 *
 * 使い方:
 *   EmotionStateDao dao = new EmotionStateDao(conn);
 *   dao.save(player);        // 週次処理後に呼ぶ
 *   dao.load(player);        // DB 復元時に呼ぶ
 */
public class EmotionStateDao {

    private final Connection conn;

    public EmotionStateDao(Connection conn) {
        this.conn = conn;
    }

    // ─────────────────────────────────────────────────────────
    // テーブル作成（初回起動時に呼ぶ）
    // ─────────────────────────────────────────────────────────

    public void createTableIfNotExists() throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS player_emotions (
                player_id    INT          NOT NULL,
                confidence   INT          NOT NULL DEFAULT 60,
                frustration  INT          NOT NULL DEFAULT 0,
                loyalty      INT          NOT NULL DEFAULT 50,
                rivalry      INT          NOT NULL DEFAULT 0,
                mood         VARCHAR(10)  NOT NULL DEFAULT 'NORMAL',
                benched_weeks INT         NOT NULL DEFAULT 0,
                updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
                    ON UPDATE CURRENT_TIMESTAMP,
                PRIMARY KEY (player_id),
                CONSTRAINT fk_emotion_player
                    FOREIGN KEY (player_id) REFERENCES players(id)
                    ON DELETE CASCADE
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;
        try (Statement st = conn.createStatement()) {
            st.executeUpdate(sql);
        }
    }

    // ─────────────────────────────────────────────────────────
    // 保存
    // ─────────────────────────────────────────────────────────

    /**
     * 選手の EmotionState を DB に保存（UPSERT）
     */
    public void save(Player player) throws SQLException {
        EmotionState e = player.getEmotion();
        String sql = """
            INSERT INTO player_emotions
                (player_id, confidence, frustration, loyalty, rivalry, mood, benched_weeks)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                confidence    = VALUES(confidence),
                frustration   = VALUES(frustration),
                loyalty       = VALUES(loyalty),
                rivalry       = VALUES(rivalry),
                mood          = VALUES(mood),
                benched_weeks = VALUES(benched_weeks)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt   (1, player.getId());
            ps.setInt   (2, e.getConfidence());
            ps.setInt   (3, e.getFrustration());
            ps.setInt   (4, e.getLoyalty());
            ps.setInt   (5, e.getRivalry());
            ps.setString(6, e.getMood().name());
            ps.setInt   (7, e.getBenchedWeeks());
            ps.executeUpdate();
        }
    }

    /**
     * クラブ全選手の感情を一括保存（週次処理後に呼ぶ）
     */
    public void saveAll(java.util.List<Player> players) throws SQLException {
        for (Player p : players) {
            if (p.getId() > 0) save(p);
        }
    }

    // ─────────────────────────────────────────────────────────
    // 読み込み
    // ─────────────────────────────────────────────────────────

    /**
     * DB から感情パラメータを読み込み、Player にセットする
     * レコードがなければ初期値のまま（EmotionState のデフォルト）
     */
    public void load(Player player) throws SQLException {
        String sql = """
            SELECT confidence, frustration, loyalty, rivalry, mood, benched_weeks
            FROM player_emotions
            WHERE player_id = ?
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, player.getId());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    EmotionState e = player.getEmotion();
                    e.setConfidence  (rs.getInt("confidence"));
                    e.setFrustration (rs.getInt("frustration"));
                    e.setLoyalty     (rs.getInt("loyalty"));
                    e.setRivalry     (rs.getInt("rivalry"));
                    e.setBenchedWeeks(rs.getInt("benched_weeks"));
                    try {
                        e.setMood(Mood.valueOf(rs.getString("mood")));
                    } catch (IllegalArgumentException ex) {
                        e.setMood(Mood.NORMAL); // 不正値はデフォルト
                    }
                }
                // レコードなし → EmotionState のデフォルト値をそのまま使う
            }
        }
    }

    /**
     * クラブ全選手の感情を一括読み込み
     */
    public void loadAll(java.util.List<Player> players) throws SQLException {
        if (players.isEmpty()) return;

        // IN句でまとめて取得（N+1を避ける）
        String ids = players.stream()
            .mapToInt(Player::getId)
            .filter(id -> id > 0)
            .mapToObj(String::valueOf)
            .reduce((a, b) -> a + "," + b)
            .orElse("0");

        String sql = """
            SELECT player_id, confidence, frustration, loyalty, rivalry, mood, benched_weeks
            FROM player_emotions
            WHERE player_id IN (""" + ids + ")";

        // ID → Player のマップを作成
        java.util.Map<Integer, Player> map = new java.util.HashMap<>();
        for (Player p : players) map.put(p.getId(), p);

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Player p = map.get(rs.getInt("player_id"));
                if (p == null) continue;
                EmotionState e = p.getEmotion();
                e.setConfidence  (rs.getInt("confidence"));
                e.setFrustration (rs.getInt("frustration"));
                e.setLoyalty     (rs.getInt("loyalty"));
                e.setRivalry     (rs.getInt("rivalry"));
                e.setBenchedWeeks(rs.getInt("benched_weeks"));
                try {
                    e.setMood(Mood.valueOf(rs.getString("mood")));
                } catch (IllegalArgumentException ex) {
                    e.setMood(Mood.NORMAL);
                }
            }
        }
    }
}
