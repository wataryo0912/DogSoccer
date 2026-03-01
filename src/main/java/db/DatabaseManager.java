package db;

import java.sql.*;
import db.WeeklyEventDao;
import db.FriendshipDao;
import java.util.Properties;

/**
 * DB接続管理（MySQL / SQLite 切り替え対応）
 *
 * ─────────────────────────────────────────────────────────
 * 【テーブル構成】
 *
 *   clubs               チーム基本情報・予算・成績
 *   players             選手情報（能力値・契約・外観）
 *   matches             試合結果
 *   match_stats         試合スタッツ（シュート・コーナー等）
 *   transfers           移籍履歴
 *   special_moves       必殺技マスター
 *   players_special_moves  選手↔必殺技 中間テーブル
 *   images              画像ファイル管理マスター（NEW）
 *
 * 【images テーブル設計】
 *   選手写真・ユニフォーム・必殺技演出画像を一元管理。
 *   players.image_file / clubs.uniform_image / special_moves.image_file
 *   はすべて images.file_name を参照する論理的な外部キー。
 *   （DB外部キー制約ではなくアプリ側で解決）
 *
 *   今後の運用:
 *     - 新選手追加: images テーブルに登録 → players.image_file に設定
 *     - 画像差し替え: images テーブルの file_name を更新するだけ
 *     - GUIで一覧管理: SELECT * FROM images WHERE image_type='PLAYER'
 * ─────────────────────────────────────────────────────────
 */
public class DatabaseManager {

    private static DatabaseManager instance;
    private Connection connection;

    private DatabaseManager() {}

    public static DatabaseManager getInstance() {
        if (instance == null) instance = new DatabaseManager();
        return instance;
    }

    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            // ドライバをロード
            try {
                Class.forName(DatabaseConfig.getDriverClass());
            } catch (ClassNotFoundException e) {
                String jar = DatabaseConfig.isMySql()
                    ? "mysql-connector-j-*.jar  (https://dev.mysql.com/downloads/connector/j/)"
                    : "sqlite-jdbc-*.jar  (https://github.com/xerial/sqlite-jdbc/releases)";
                throw new SQLException(
                    "JDBCドライバが見つかりません。lib/ に " + jar + " を配置してください。", e);
            }

            if (DatabaseConfig.isMySql()) {
                Properties props = new Properties();
                props.setProperty("user",               DatabaseConfig.getUser());
                props.setProperty("password",           DatabaseConfig.getPassword());
                props.setProperty("useUnicode",         "true");
                props.setProperty("characterEncoding",  "UTF-8");
                connection = DriverManager.getConnection(DatabaseConfig.getJdbcUrl(), props);
            } else {
                connection = DriverManager.getConnection(DatabaseConfig.getJdbcUrl());
            }
            connection.setAutoCommit(true);
            System.out.println("[DB] 接続確立: " + DatabaseConfig.getDbType().toUpperCase());
        }
        return connection;
    }

    public void initializeDatabase() {
        try (Connection conn = getConnection();
             Statement  stmt = conn.createStatement()) {

            if (!DatabaseConfig.isMySql()) {
                stmt.execute("PRAGMA foreign_keys = ON");
            }

            createTables(stmt);
            runMigrations(stmt);

            System.out.println("[DB] 初期化完了: " + DatabaseConfig.getDbType().toUpperCase());
        } catch (SQLException e) {
            System.err.println("[DB] 初期化エラー: " + e.getMessage());
            throw new RuntimeException("DB初期化失敗", e);
        }
    }

    private void createTables(Statement stmt) throws SQLException {
        boolean mysql = DatabaseConfig.isMySql();
        String  AI    = DatabaseConfig.autoIncrement();
        String  OPT   = DatabaseConfig.tableOptions();
        String  BOOL  = DatabaseConfig.boolType();
        String  NOWFN = mysql ? "NOW()" : "datetime('now','localtime')";

        // ── images（画像マスター） ────────────────────────────────
        // image_type: PLAYER / UNIFORM / SPECIAL_MOVE / OTHER
        stmt.execute(String.format("""
            CREATE TABLE IF NOT EXISTS images (
                id          INTEGER PRIMARY KEY %s,
                file_name   VARCHAR(255) NOT NULL UNIQUE,
                image_type  VARCHAR(20)  NOT NULL DEFAULT 'OTHER',
                description VARCHAR(255) NOT NULL DEFAULT '',
                created_at  DATETIME     DEFAULT (%s)
            )%s
        """, AI, NOWFN, OPT));

        // ── clubs ─────────────────────────────────────────────────
        stmt.execute(String.format("""
            CREATE TABLE IF NOT EXISTS clubs (
                id                   INTEGER      PRIMARY KEY %s,
                name                 VARCHAR(100) NOT NULL UNIQUE,
                color                VARCHAR(20)  NOT NULL DEFAULT '#ff6b00',
                formation            VARCHAR(20)  NOT NULL DEFAULT '4-4-2',
                breed                VARCHAR(100) NOT NULL DEFAULT '柴犬',
                uniform_image        VARCHAR(255) NOT NULL DEFAULT '',
                budget               BIGINT       NOT NULL DEFAULT 0,
                weekly_salary_budget BIGINT       NOT NULL DEFAULT 0,
                wins                 INTEGER      NOT NULL DEFAULT 0,
                draws                INTEGER      NOT NULL DEFAULT 0,
                losses               INTEGER      NOT NULL DEFAULT 0,
                created_at           DATETIME     DEFAULT (%s)
            )%s
        """, AI, NOWFN, OPT));

        // ── players ───────────────────────────────────────────────
        stmt.execute(String.format("""
            CREATE TABLE IF NOT EXISTS players (
                id             INTEGER      PRIMARY KEY %s,
                club_id        INTEGER      REFERENCES clubs(id) ON DELETE SET NULL,
                name           VARCHAR(100) NOT NULL,
                last_name      VARCHAR(100) NOT NULL DEFAULT '',
                age            INTEGER      NOT NULL DEFAULT 4,
                nationality    VARCHAR(50)  NOT NULL DEFAULT '犬',
                breed          VARCHAR(100) NOT NULL DEFAULT '柴犬',
                position       VARCHAR(10)  NOT NULL,
                overall        INTEGER      NOT NULL,
                potential      INTEGER      NOT NULL,
                salary         BIGINT       NOT NULL DEFAULT 0,
                market_value   BIGINT       NOT NULL DEFAULT 0,
                contract_years INTEGER      NOT NULL DEFAULT 1,
                speed          INTEGER      NOT NULL DEFAULT 50,
                shooting       INTEGER      NOT NULL DEFAULT 50,
                passing        INTEGER      NOT NULL DEFAULT 50,
                defending      INTEGER      NOT NULL DEFAULT 50,
                stamina        INTEGER      NOT NULL DEFAULT 50,
                spirit         INTEGER      NOT NULL DEFAULT 70,
                uniform_name   VARCHAR(50)  NOT NULL DEFAULT '',
                shirt_number   INTEGER      NOT NULL DEFAULT 0,
                dominant_foot  VARCHAR(10)  NOT NULL DEFAULT '右',
                is_captain     %s           NOT NULL DEFAULT 0,
                player_role           VARCHAR(20)  NOT NULL DEFAULT 'REGISTERED',
                retirement_announced  INTEGER      NOT NULL DEFAULT 0,
                reincarnation_season  INTEGER      NOT NULL DEFAULT -1,
                former_club_name      VARCHAR(100) NOT NULL DEFAULT '',
                image_file            VARCHAR(255) NOT NULL DEFAULT '',
                created_at     DATETIME     DEFAULT (%s)
            )%s
        """, AI, BOOL, NOWFN, OPT));

        // ── matches ───────────────────────────────────────────────
        stmt.execute(String.format("""
            CREATE TABLE IF NOT EXISTS matches (
                id           INTEGER  PRIMARY KEY %s,
                home_club_id INTEGER  NOT NULL REFERENCES clubs(id),
                away_club_id INTEGER  NOT NULL REFERENCES clubs(id),
                home_goals   INTEGER  NOT NULL DEFAULT 0,
                away_goals   INTEGER  NOT NULL DEFAULT 0,
                played_at    DATETIME DEFAULT (%s)
            )%s
        """, AI, NOWFN, OPT));

        // ── match_stats ───────────────────────────────────────────
        stmt.execute(String.format("""
            CREATE TABLE IF NOT EXISTS match_stats (
                id            INTEGER PRIMARY KEY %s,
                match_id      INTEGER NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
                home_shots    INTEGER DEFAULT 0, away_shots    INTEGER DEFAULT 0,
                home_shots_on INTEGER DEFAULT 0, away_shots_on INTEGER DEFAULT 0,
                home_corners  INTEGER DEFAULT 0, away_corners  INTEGER DEFAULT 0,
                home_fouls    INTEGER DEFAULT 0, away_fouls    INTEGER DEFAULT 0,
                home_poss     INTEGER DEFAULT 50
            )%s
        """, AI, OPT));

        // ── transfers ─────────────────────────────────────────────
        stmt.execute(String.format("""
            CREATE TABLE IF NOT EXISTS transfers (
                id             INTEGER  PRIMARY KEY %s,
                player_id      INTEGER  NOT NULL REFERENCES players(id),
                from_club_id   INTEGER  REFERENCES clubs(id),
                to_club_id     INTEGER  REFERENCES clubs(id),
                transfer_fee   BIGINT   NOT NULL DEFAULT 0,
                transferred_at DATETIME DEFAULT (%s)
            )%s
        """, AI, NOWFN, OPT));

        // ── special_moves ─────────────────────────────────────────
        stmt.execute(String.format("""
            CREATE TABLE IF NOT EXISTS special_moves (
                id          INTEGER      PRIMARY KEY %s,
                name        VARCHAR(100) NOT NULL UNIQUE,
                description VARCHAR(255) NOT NULL DEFAULT '',
                image_file  VARCHAR(255) NOT NULL DEFAULT '',
                power       INTEGER      NOT NULL DEFAULT 50,
                move_type   VARCHAR(20)  NOT NULL DEFAULT 'SHOOT'
            )%s
        """, AI, OPT));

        // ── weekly_events（週次イベント履歴） ──────────────────────────
        stmt.execute(WeeklyEventDao.createTableSql());

        // ── friendships（選手間仲良し度） ────────────────────────────
        stmt.execute(FriendshipDao.createTableSql());

        // ── players_special_moves（中間テーブル） ─────────────────
        if (mysql) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS players_special_moves (
                    player_id       INTEGER NOT NULL,
                    special_move_id INTEGER NOT NULL,
                    PRIMARY KEY (player_id),
                    UNIQUE KEY uq_player (player_id),
                    CONSTRAINT fk_psm_player FOREIGN KEY (player_id)
                        REFERENCES players(id) ON DELETE CASCADE,
                    CONSTRAINT fk_psm_move   FOREIGN KEY (special_move_id)
                        REFERENCES special_moves(id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
        } else {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS players_special_moves (
                    player_id       INTEGER NOT NULL REFERENCES players(id) ON DELETE CASCADE,
                    special_move_id INTEGER NOT NULL REFERENCES special_moves(id) ON DELETE CASCADE,
                    PRIMARY KEY (player_id),
                    UNIQUE (player_id)
                )
            """);
        }
    }

    /** 既存DBへのカラム追加（エラーは無視） */
    private void runMigrations(Statement stmt) {
        safeAlter(stmt, "ALTER TABLE clubs   ADD COLUMN color VARCHAR(20) NOT NULL DEFAULT '#ff6b00'");
        safeAlter(stmt, "ALTER TABLE clubs   ADD COLUMN formation VARCHAR(20) NOT NULL DEFAULT '4-4-2'");
        safeAlter(stmt, "ALTER TABLE clubs   ADD COLUMN breed VARCHAR(100) NOT NULL DEFAULT '柴犬'");
        safeAlter(stmt, "ALTER TABLE clubs   ADD COLUMN uniform_image VARCHAR(255) NOT NULL DEFAULT ''");
        safeAlter(stmt, "ALTER TABLE players ADD COLUMN last_name VARCHAR(100) NOT NULL DEFAULT ''");
        safeAlter(stmt, "ALTER TABLE players ADD COLUMN uniform_name VARCHAR(50) NOT NULL DEFAULT ''");
        safeAlter(stmt, "ALTER TABLE players ADD COLUMN shirt_number INTEGER NOT NULL DEFAULT 0");
        safeAlter(stmt, "ALTER TABLE players ADD COLUMN dominant_foot VARCHAR(10) NOT NULL DEFAULT '右'");
        safeAlter(stmt, "ALTER TABLE players ADD COLUMN is_captain INTEGER NOT NULL DEFAULT 0");
        safeAlter(stmt, "ALTER TABLE players ADD COLUMN image_file VARCHAR(255) NOT NULL DEFAULT ''");
        safeAlter(stmt, "ALTER TABLE players ADD COLUMN spirit INTEGER NOT NULL DEFAULT 70");
        safeAlter(stmt, "ALTER TABLE players ADD COLUMN player_role VARCHAR(20) NOT NULL DEFAULT 'REGISTERED'");
        safeAlter(stmt, "ALTER TABLE players ADD COLUMN retirement_announced INTEGER NOT NULL DEFAULT 0");
        safeAlter(stmt, "ALTER TABLE players ADD COLUMN reincarnation_season INTEGER NOT NULL DEFAULT -1");
        safeAlter(stmt, "ALTER TABLE players ADD COLUMN former_club_name VARCHAR(100) NOT NULL DEFAULT ''");
    }

    private void safeAlter(Statement stmt, String sql) {
        try { stmt.execute(sql); } catch (SQLException ignored) {}
    }

    /**
     * MySQLのINSERT ... ON DUPLICATE KEY UPDATE を扱うためのヘルパー。
     * SQLiteでは INSERT OR REPLACE INTO を使う。
     */
    public boolean isMySql() { return DatabaseConfig.isMySql(); }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("[DB] 接続を閉じました");
            }
        } catch (SQLException e) {
            System.err.println("[DB] クローズエラー: " + e.getMessage());
        }
    }
}
