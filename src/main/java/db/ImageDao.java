package db;

import java.sql.*;
import java.util.*;

/**
 * 画像マスターテーブル (images) の操作クラス
 *
 * ─────────────────────────────────────────────────────────
 * 【設計思想】
 *   Excel/CSV の画像参照を DB で一元管理する。
 *   players.image_file / clubs.uniform_image / special_moves.image_file
 *   はすべて images.file_name の値を参照する（アプリ側解決）。
 *
 * 【image_type 一覧】
 *   PLAYER       選手写真
 *   UNIFORM      チームユニフォーム
 *   SPECIAL_MOVE 必殺技演出画像
 *   BADGE        クラブバッジ/エンブレム
 *   OTHER        その他
 *
 * 【運用手順（新画像追加）】
 *   1. resources/<file_name> に画像ファイルを置く
 *   2. images テーブルに INSERT（このクラスの upsert() を使う）
 *   3. players.image_file or clubs.uniform_image に file_name をセット
 *
 * 【MySQL管理用クエリ例】
 *   -- 全画像一覧
 *   SELECT * FROM images ORDER BY image_type, file_name;
 *   -- 未使用画像を検出
 *   SELECT i.file_name FROM images i
 *   WHERE i.file_name NOT IN (SELECT DISTINCT image_file FROM players WHERE image_file != '')
 *     AND i.file_name NOT IN (SELECT DISTINCT uniform_image FROM clubs WHERE uniform_image != '')
 *     AND i.file_name NOT IN (SELECT DISTINCT image_file FROM special_moves WHERE image_file != '');
 * ─────────────────────────────────────────────────────────
 */
public class ImageDao {

    public enum ImageType {
        PLAYER, UNIFORM, SPECIAL_MOVE, BADGE, OTHER
    }

    public record ImageRecord(int id, String fileName, ImageType imageType,
                               String description) {}

    private final DatabaseManager db = DatabaseManager.getInstance();

    // ── INSERT / UPSERT ──────────────────────────────────────
    public int upsert(String fileName, ImageType type, String description) throws SQLException {
        int existing = findIdByFileName(fileName);
        if (existing > 0) {
            // 説明だけ更新
            try (PreparedStatement ps = db.getConnection().prepareStatement(
                    "UPDATE images SET image_type=?, description=? WHERE id=?")) {
                ps.setString(1, type.name());
                ps.setString(2, description);
                ps.setInt   (3, existing);
                ps.executeUpdate();
            }
            return existing;
        }
        return insert(fileName, type, description);
    }

    public int insert(String fileName, ImageType type, String description) throws SQLException {
        String sql = """
            INSERT INTO images (file_name, image_type, description)
            VALUES (?, ?, ?)
        """;
        try (PreparedStatement ps = db.getConnection()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, fileName);
            ps.setString(2, type.name());
            ps.setString(3, description != null ? description : "");
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            return rs.next() ? rs.getInt(1) : -1;
        }
    }

    // ── SELECT ───────────────────────────────────────────────
    public List<ImageRecord> findAll() throws SQLException {
        List<ImageRecord> list = new ArrayList<>();
        try (Statement stmt = db.getConnection().createStatement();
             ResultSet rs   = stmt.executeQuery(
                     "SELECT * FROM images ORDER BY image_type, file_name")) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public List<ImageRecord> findByType(ImageType type) throws SQLException {
        List<ImageRecord> list = new ArrayList<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT * FROM images WHERE image_type=? ORDER BY file_name")) {
            ps.setString(1, type.name());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public int findIdByFileName(String fileName) throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT id FROM images WHERE file_name=?")) {
            ps.setString(1, fileName);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt("id") : -1;
        }
    }

    // ── DELETE ───────────────────────────────────────────────
    public void delete(int id) throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "DELETE FROM images WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    // ── 初期データ登録（アプリ起動時に呼ぶ） ─────────────────
    /**
     * resources/ フォルダの既知画像を images テーブルに登録する。
     * 既存エントリは上書きしない（INSERT IGNORE 相当）。
     */
    public void seedKnownImages() {
        Object[][] known = {
            // {fileName,         type,         description}
            {"Gemini_Generated_Image_92nowe92nowe92no.png", ImageType.PLAYER,       "ぶん助 選手写真"},
            {"Gemini_Generated_Image_9jfx379jfx379jfx.png", ImageType.UNIFORM,     "クラブ・ぶん助 ユニフォーム"},
            {"img_bunsuke.png",                              ImageType.PLAYER,       "ぶん助 アイコン"},
            {"img_uniform.png",                              ImageType.UNIFORM,      "ユニフォームデザイン"},
            {"move_bunsuke.png",                             ImageType.SPECIAL_MOVE, "流星ブルアタック 演出画像"},
        };
        for (Object[] row : known) {
            try {
                String fn   = (String) row[0];
                ImageType t = (ImageType) row[1];
                String desc = (String) row[2];
                if (findIdByFileName(fn) < 0) {
                    insert(fn, t, desc);
                    System.out.println("[ImageDao] 登録: " + fn);
                }
            } catch (SQLException e) {
                System.err.println("[ImageDao] シードエラー: " + e.getMessage());
            }
        }
    }

    private ImageRecord map(ResultSet rs) throws SQLException {
        return new ImageRecord(
            rs.getInt   ("id"),
            rs.getString("file_name"),
            ImageType.valueOf(rs.getString("image_type")),
            rs.getString("description")
        );
    }
}
