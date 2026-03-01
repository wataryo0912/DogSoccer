package db;

import model.SpecialMove;
import model.Player;
import db.DatabaseConfig;

import java.sql.*;
import java.util.*;

/**
 * 必殺技のDB操作クラス
 *
 * 【テーブル構成】
 *   special_moves            : 必殺技マスター（技名・説明・画像・威力・タイプ）
 *   players_special_moves    : 選手と必殺技の紐づけ（player_id → special_move_id）
 *
 * 【使い方】
 *   // 必殺技を登録
 *   int moveId = dao.insertMove(new SpecialMove("流星一蹴", "渾身の一蹴！", "img_move1.png", 80, MoveType.SHOOT));
 *   // 選手に紐づけ
 *   dao.assignToPlayer(playerId, moveId);
 *   // 選手の必殺技を取得
 *   SpecialMove move = dao.findByPlayerId(playerId);
 */
public class SpecialMoveDao {

    private final DatabaseManager db = DatabaseManager.getInstance();

    // ── INSERT ──────────────────────────────────────────────────
    public int insertMove(SpecialMove m) throws SQLException {
        // MySQL: INSERT IGNORE INTO / SQLite: INSERT OR IGNORE INTO
        String insertKeyword = DatabaseConfig.isMySql()
            ? "INSERT IGNORE INTO" : "INSERT OR IGNORE INTO";
        String sql = insertKeyword + """ 
             special_moves (name, description, image_file, power, move_type)
            VALUES (?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, m.getName());
            ps.setString(2, m.getDescription());
            ps.setString(3, m.getImageFile());
            ps.setInt   (4, m.getPower());
            ps.setString(5, m.getMoveType().name());
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) { int id = rs.getInt(1); m.setId(id); return id; }
            // INSERT IGNOREで既存の場合はIDを取得
            return findIdByName(m.getName());
        }
    }

    /** 選手に必殺技を紐づける（1選手1技） */
    public void assignToPlayer(int playerId, int specialMoveId) throws SQLException {
        String prefix = DatabaseConfig.isMySql()
            ? "INSERT INTO players_special_moves (player_id, special_move_id) VALUES (?, ?) ON DUPLICATE KEY UPDATE special_move_id=VALUES(special_move_id)"
            : "INSERT OR REPLACE INTO players_special_moves (player_id, special_move_id) VALUES (?, ?)";
        String sql = prefix;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setInt(1, playerId);
            ps.setInt(2, specialMoveId);
            ps.executeUpdate();
        }
    }

    /** 選手IDで必殺技を取得 */
    public SpecialMove findByPlayerId(int playerId) throws SQLException {
        String sql = """
            SELECT sm.* FROM special_moves sm
            JOIN players_special_moves psm ON sm.id = psm.special_move_id
            WHERE psm.player_id = ?
        """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setInt(1, playerId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? map(rs) : null;
        }
    }

    /** 全必殺技を取得 */
    public List<SpecialMove> findAllMoves() throws SQLException {
        List<SpecialMove> list = new ArrayList<>();
        try (Statement stmt = db.getConnection().createStatement();
             ResultSet rs   = stmt.executeQuery("SELECT * FROM special_moves ORDER BY id")) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    /** 名前でIDを取得 */
    public int findIdByName(String name) throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT id FROM special_moves WHERE name = ?")) {
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt("id") : -1;
        }
    }

    /**
     * 初期必殺技データを投入（DBが空の場合のみ）
     * クラブ・ぶん助の選手11名にそれぞれ固有の必殺技を設定
     */
    public void seedDefaultMoves(List<Player> squad) throws SQLException {
        if (!findAllMoves().isEmpty()) return;  // 既存データがあればスキップ

        // デフォルト必殺技定義（選手ポジションに合わせたタイプ）
        Object[][] moves = {
            // {uniformName, 技名, 説明, 画像ファイル名（後で差し替え可）, 威力, タイプ}
            {"BUNSUKE",   "流星ブルアタック",  "青い炎を纏った必殺の一蹴！スタジアムが青白く染まる！", "move_bunsuke.png",   95, SpecialMove.MoveType.SHOOT},
            {"BURU",      "ブル旋風脚",        "竜巻のように回転して突破！",         "move_buru.png",      75, SpecialMove.MoveType.DRIBBLE},
            {"KINTARO",   "大岩守護壁",        "岩のような体で全てを跳ね返す！",     "move_kintaro.png",   80, SpecialMove.MoveType.TACKLE},
            {"TOMEJIRO",  "老練の一閃",        "80歳の経験が生む神業パス！",         "move_tomejiro.png",  70, SpecialMove.MoveType.SPIRIT},
            {"KIKUJIRO",  "疾風縦突破",        "サイドを切り裂く超高速ドリブル！",   "move_kikujiro.png",  72, SpecialMove.MoveType.DRIBBLE},
            {"TAKESHI",   "鋼鉄の前足",        "GK武の鉄壁セーブ！",               "move_takeshi.png",   85, SpecialMove.MoveType.SAVE},
            {"SEBASTIAN", "エレガントビジョン","未来を見通す天才的司令塔プレー！",   "move_sebastian.png", 78, SpecialMove.MoveType.DRIBBLE},
            {"TANAKA",    "ブルドッグタックル", "唸る体当たりで相手をなぎ倒す！",    "move_tanaka.png",    82, SpecialMove.MoveType.TACKLE},
            {"HANAO",     "嗅覚スキャン",      "鼻でボールの軌道を完全予測！",       "move_hanao.png",     74, SpecialMove.MoveType.TACKLE},
            {"ANBE",      "餡兵衛バウンド",    "予測不能な変幻自在のドリブル！",     "move_anbe.png",      71, SpecialMove.MoveType.DRIBBLE},
            {"GASTON",    "欧州仕込みウィング","超速カットインからの豪快シュート！",  "move_gaston.png",    80, SpecialMove.MoveType.SHOOT},
        };

        for (Object[] m : moves) {
            SpecialMove sm = new SpecialMove(
                (String) m[1], (String) m[2], (String) m[3],
                (Integer) m[4], (SpecialMove.MoveType) m[5]
            );
            int smId = insertMove(sm);

            // uniformNameで選手を探して紐づけ
            String uniName = (String) m[0];
            squad.stream()
                .filter(p -> uniName.equals(p.getUniformName()))
                .findFirst()
                .ifPresent(p -> {
                    try {
                        if (p.getId() > 0) {
                            assignToPlayer(p.getId(), smId);
                            p.setSpecialMove(sm);
                            System.out.println("[SpecialMove] " + p.getFullName()
                                + " → 【" + sm.getName() + "】");
                        }
                    } catch (SQLException ex) {
                        System.err.println("[SpecialMove] 紐づけエラー: " + ex.getMessage());
                    }
                });
        }
    }

    /**
     * スカッド全員の必殺技をDBからロードしてPlayerにセット
     */
    public void loadSpecialMovesForSquad(List<Player> squad) throws SQLException {
        for (Player p : squad) {
            if (p.getId() > 0) {
                SpecialMove sm = findByPlayerId(p.getId());
                if (sm != null) p.setSpecialMove(sm);
            }
        }
    }

    private SpecialMove map(ResultSet rs) throws SQLException {
        SpecialMove sm = new SpecialMove(
            rs.getString("name"),
            rs.getString("description"),
            rs.getString("image_file"),
            rs.getInt   ("power"),
            SpecialMove.MoveType.valueOf(rs.getString("move_type"))
        );
        sm.setId(rs.getInt("id"));
        return sm;
    }
}
