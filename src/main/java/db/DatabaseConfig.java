package db;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * DB接続設定の読み込み
 *
 * resources/db.properties を読み込む。
 * db.type=mysql  → MySQL を使用
 * db.type=sqlite → SQLite を使用（デフォルト）
 *
 * 【MySQL セットアップ手順】
 *   1. MySQL 8.x をインストール
 *   2. mysql-connector-j-*.jar を lib/ に配置
 *      https://dev.mysql.com/downloads/connector/j/
 *   3. 以下のSQLを実行してDB・ユーザーを作成:
 *      CREATE DATABASE soccer_manager CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
 *      CREATE USER 'soccer_user'@'localhost' IDENTIFIED BY 'soccer_pass';
 *      GRANT ALL PRIVILEGES ON soccer_manager.* TO 'soccer_user'@'localhost';
 *      FLUSH PRIVILEGES;
 *   4. resources/db.properties の db.type=mysql に変更
 *   5. mysql.host / mysql.user / mysql.password を設定
 */
public class DatabaseConfig {

    private static final Properties props = new Properties();
    private static boolean loaded = false;

    static {
        load();
    }

    private static void load() {
        // まずクラスパス上の db.properties を探す
        try (InputStream is = DatabaseConfig.class.getClassLoader()
                .getResourceAsStream("db.properties")) {
            if (is != null) {
                props.load(is);
                loaded = true;
                System.out.println("[DB] db.properties 読み込み完了: type=" + getDbType());
                return;
            }
        } catch (IOException ignored) {}

        // なければ resources/ フォルダを直接探す
        try (InputStream is = new java.io.FileInputStream("resources/db.properties")) {
            props.load(is);
            loaded = true;
            System.out.println("[DB] resources/db.properties 読み込み完了: type=" + getDbType());
        } catch (IOException e) {
            System.out.println("[DB] db.properties が見つかりません。SQLiteをデフォルト使用します");
            props.setProperty("db.type", "sqlite");
            props.setProperty("sqlite.file", "soccer_manager.db");
        }
    }

    public static String getDbType() {
        return props.getProperty("db.type", "sqlite").toLowerCase();
    }

    public static boolean isMySql() {
        return "mysql".equals(getDbType());
    }

    // ── MySQL ──────────────────────────────────────────────────
    public static String getMysqlUrl() {
        String host   = props.getProperty("mysql.host",     "localhost");
        String port   = props.getProperty("mysql.port",     "3306");
        String db     = props.getProperty("mysql.database", "soccer_manager");
        String opts   = props.getProperty("mysql.options",
            "useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Tokyo&characterEncoding=UTF-8");
        return String.format("jdbc:mysql://%s:%s/%s?%s", host, port, db, opts);
    }

    public static String getMysqlUser() {
        return props.getProperty("mysql.user", "root");
    }

    public static String getMysqlPassword() {
        return props.getProperty("mysql.password", "");
    }

    // ── SQLite ─────────────────────────────────────────────────
    public static String getSqliteUrl() {
        return "jdbc:sqlite:" + props.getProperty("sqlite.file", "soccer_manager.db");
    }

    // ── JDBC URL 統合（型に応じて返す） ───────────────────────
    public static String getJdbcUrl() {
        return isMySql() ? getMysqlUrl() : getSqliteUrl();
    }

    public static String getUser()     { return isMySql() ? getMysqlUser()     : null; }
    public static String getPassword() { return isMySql() ? getMysqlPassword() : null; }

    public static String getDriverClass() {
        return isMySql() ? "com.mysql.cj.jdbc.Driver" : "org.sqlite.JDBC";
    }

    // ── DDL差異の吸収 ─────────────────────────────────────────
    /** AUTO_INCREMENT（MySQL）/ AUTOINCREMENT（SQLite） */
    public static String autoIncrement() {
        return isMySql() ? "AUTO_INCREMENT" : "AUTOINCREMENT";
    }

    /** 現在時刻関数 */
    public static String now() {
        return isMySql() ? "NOW()" : "datetime('now','localtime')";
    }

    /** 文字列型（MySQLはTEXT/VARCHARを使い分け） */
    public static String varcharOrText(int len) {
        return isMySql() ? "VARCHAR(" + len + ")" : "TEXT";
    }

    /** エンジン指定（MySQLのみ） */
    public static String tableOptions() {
        return isMySql() ? " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci" : "";
    }

    /** UPSERT文（MySQL: INSERT ... ON DUPLICATE KEY UPDATE / SQLite: INSERT OR REPLACE） */
    public static String upsertPrefix() {
        return isMySql() ? "INSERT INTO" : "INSERT OR REPLACE INTO";
    }

    /** UPSERT後置（MySQL: ON DUPLICATE KEY UPDATE ... / SQLite: 不要） */
    public static String onDuplicateUpdate(String... columnAssignments) {
        if (!isMySql()) return "";
        return " ON DUPLICATE KEY UPDATE " + String.join(", ", columnAssignments);
    }

    /** LAST_INSERT_ID()（MySQL）/ last_insert_rowid()（SQLite） */
    public static String lastInsertId() {
        return isMySql() ? "SELECT LAST_INSERT_ID()" : "SELECT last_insert_rowid()";
    }

    /** BOOLEAN型 */
    public static String boolType() {
        return isMySql() ? "TINYINT(1)" : "INTEGER";
    }

    @Override
    public String toString() {
        return String.format("DatabaseConfig{type=%s, url=%s}", getDbType(), getJdbcUrl());
    }
}
