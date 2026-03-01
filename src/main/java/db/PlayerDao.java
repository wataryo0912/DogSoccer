package db;

import model.Player;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 選手DB操作クラス
 * image_file カラムを含む全フィールドを管理する。
 */
public class PlayerDao {

    private final DatabaseManager db = DatabaseManager.getInstance();

    public int insert(Player p, int clubId) throws SQLException {
        String sql = """
            INSERT INTO players
              (club_id, name, last_name, age, nationality, breed, position,
               overall, potential, salary, market_value, contract_years,
               speed, shooting, passing, defending, stamina, spirit,
               uniform_name, shirt_number, dominant_foot, is_captain,
               player_role, retirement_announced, reincarnation_season, former_club_name,
               image_file)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, p, clubId);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            int id = rs.next() ? rs.getInt(1) : -1;
            p.setId(id);
            return id;
        }
    }

    public List<Player> findAll() throws SQLException {
        return query("SELECT * FROM players ORDER BY club_id, shirt_number, overall DESC");
    }

    public List<Player> findByClubId(int clubId) throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT * FROM players WHERE club_id=? ORDER BY shirt_number, position, overall DESC")) {
            ps.setInt(1, clubId);
            return extract(ps.executeQuery());
        }
    }

    public List<Player> findFreeAgents() throws SQLException {
        return query("SELECT * FROM players WHERE club_id IS NULL ORDER BY overall DESC");
    }

    public Player findById(int id) throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT * FROM players WHERE id=?")) {
            ps.setInt(1, id);
            List<Player> list = extract(ps.executeQuery());
            return list.isEmpty() ? null : list.get(0);
        }
    }

    public void update(Player p) throws SQLException {
        String sql = """
            UPDATE players SET
                name=?, last_name=?, age=?, nationality=?, breed=?, position=?,
                overall=?, potential=?, salary=?, market_value=?, contract_years=?,
                speed=?, shooting=?, passing=?, defending=?, stamina=?, spirit=?,
                uniform_name=?, shirt_number=?, dominant_foot=?, is_captain=?,
                player_role=?, retirement_announced=?, reincarnation_season=?,
                former_club_name=?, image_file=?
            WHERE id=?
        """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1,  p.getName());
            ps.setString(2,  p.getLastName());
            ps.setInt   (3,  p.getAge());
            ps.setString(4,  p.getNationality());
            ps.setString(5,  p.getBreed());
            ps.setString(6,  p.getPosition().name());
            ps.setInt   (7,  p.getOverall());
            ps.setInt   (8,  p.getPotential());
            ps.setLong  (9,  p.getSalary());
            ps.setLong  (10, p.getMarketValue());
            ps.setInt   (11, p.getContractYears());
            ps.setInt   (12, p.getSpeed());
            ps.setInt   (13, p.getShooting());
            ps.setInt   (14, p.getPassing());
            ps.setInt   (15, p.getDefending());
            ps.setInt   (16, p.getStamina());
            ps.setInt   (17, p.getSpirit());
            ps.setString(18, p.getUniformName());
            ps.setInt   (19, p.getShirtNumber());
            ps.setString(20, p.getDominantFoot());
            ps.setInt   (21, p.isCaptain() ? 1 : 0);
            ps.setString(22, p.getRole().name());
            ps.setInt   (23, p.isRetirementAnnounced() ? 1 : 0);
            ps.setInt   (24, p.getReincarnationSeason());
            ps.setString(25, p.getFormerClubName());
            ps.setString(26, p.getImageFile());
            ps.setInt   (27, p.getId());
            ps.executeUpdate();
        }
    }

    /** 引退・転生フィールドのみ更新 */
    public void updateRetirement(Player p) throws SQLException {
        String sql = """
            UPDATE players SET
                player_role=?, retirement_announced=?,
                reincarnation_season=?, former_club_name=?
            WHERE id=?
        """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, p.getRole().name());
            ps.setInt   (2, p.isRetirementAnnounced() ? 1 : 0);
            ps.setInt   (3, p.getReincarnationSeason());
            ps.setString(4, p.getFormerClubName());
            ps.setInt   (5, p.getId());
            ps.executeUpdate();
        }
    }

    /** 転生シーズンが来た選手を取得（全クラブ横断） */
    public List<Player> findPendingReincarnations(int targetSeason) throws SQLException {
        String sql = """
            SELECT * FROM players
            WHERE reincarnation_season = ?
              AND player_role = 'RETIRED'
        """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setInt(1, targetSeason);
            return extract(ps.executeQuery());
        }
    }

    /** player_role のみ更新（昇格処理等で使用） */
    public void updateRole(int playerId, model.Player.PlayerRole role) throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "UPDATE players SET player_role=? WHERE id=?")) {
            ps.setString(1, role.name());
            ps.setInt   (2, playerId);
            ps.executeUpdate();
        }
    }

    /** pending_promotion（翌週昇格フラグ）を player_role='PENDING' で代用して保存 */
    public void markPendingPromotion(int playerId, boolean pending) throws SQLException {
        // pending=true の場合は PENDING という特殊ロールで一時保存
        // processPromotions() で REGISTERED に変換される
        String role = pending ? "PENDING" : "ACADEMY";
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "UPDATE players SET player_role=? WHERE id=?")) {
            ps.setString(1, role);
            ps.setInt   (2, playerId);
            ps.executeUpdate();
        }
    }

    /** is_captain を 1 人だけに絞るクラブ内制約チェック */
    public void enforceSingleCaptain(int clubId, int captainPlayerId) throws SQLException {
        // クラブ内の全選手を is_captain=0 に
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "UPDATE players SET is_captain=0 WHERE club_id=?")) {
            ps.setInt(1, clubId);
            ps.executeUpdate();
        }
        // 対象選手だけ is_captain=1 に
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "UPDATE players SET is_captain=1 WHERE id=? AND club_id=?")) {
            ps.setInt(1, captainPlayerId);
            ps.setInt(2, clubId);
            ps.executeUpdate();
        }
    }

    public void updateClub(int playerId, Integer clubId) throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "UPDATE players SET club_id=? WHERE id=?")) {
            if (clubId == null) ps.setNull(1, Types.INTEGER);
            else                ps.setInt (1, clubId);
            ps.setInt(2, playerId);
            ps.executeUpdate();
        }
    }

    public void delete(int playerId) throws SQLException {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "DELETE FROM players WHERE id=?")) {
            ps.setInt(1, playerId);
            ps.executeUpdate();
        }
    }

    public int upsert(Player p, int clubId) throws SQLException {
        if (p.getId() > 0) { update(p); return p.getId(); }
        return insert(p, clubId);
    }

    private void bind(PreparedStatement ps, Player p, int clubId) throws SQLException {
        if (clubId <= 0) ps.setNull(1, Types.INTEGER); else ps.setInt(1, clubId);
        ps.setString(2,  p.getName());
        ps.setString(3,  p.getLastName());
        ps.setInt   (4,  p.getAge());
        ps.setString(5,  p.getNationality());
        ps.setString(6,  p.getBreed());
        ps.setString(7,  p.getPosition().name());
        ps.setInt   (8,  p.getOverall());
        ps.setInt   (9,  p.getPotential());
        ps.setLong  (10, p.getSalary());
        ps.setLong  (11, p.getMarketValue());
        ps.setInt   (12, p.getContractYears());
        ps.setInt   (13, p.getSpeed());
        ps.setInt   (14, p.getShooting());
        ps.setInt   (15, p.getPassing());
        ps.setInt   (16, p.getDefending());
        ps.setInt   (17, p.getStamina());
        ps.setInt   (18, p.getSpirit());
        ps.setString(19, p.getUniformName());
        ps.setInt   (20, p.getShirtNumber());
        ps.setString(21, p.getDominantFoot());
        ps.setInt   (22, p.isCaptain() ? 1 : 0);
        ps.setString(23, p.getRole().name());
        ps.setInt   (24, p.isRetirementAnnounced() ? 1 : 0);
        ps.setInt   (25, p.getReincarnationSeason());
        ps.setString(26, p.getFormerClubName());
        ps.setString(27, p.getImageFile());
    }

    private List<Player> query(String sql) throws SQLException {
        try (Statement stmt = db.getConnection().createStatement();
             ResultSet rs   = stmt.executeQuery(sql)) {
            return extract(rs);
        }
    }

    private List<Player> extract(ResultSet rs) throws SQLException {
        List<Player> list = new ArrayList<>();
        while (rs.next()) list.add(map(rs));
        return list;
    }

    private Player map(ResultSet rs) throws SQLException {
        Player p = new Player(
            rs.getString("name"),
            rs.getInt   ("age"),
            rs.getString("nationality"),
            rs.getString("breed"),
            mapPosition (rs.getString("position")),
            rs.getInt   ("overall"),
            rs.getInt   ("potential"),
            rs.getLong  ("salary"),
            rs.getLong  ("market_value"),
            rs.getInt   ("contract_years")
        );
        p.setId        (rs.getInt   ("id"));
        p.setStats(rs.getInt("speed"), rs.getInt("shooting"),
                   rs.getInt("passing"), rs.getInt("defending"), rs.getInt("stamina"));
        p.setSpirit    (rs.getInt   ("spirit"));
        p.setLastName  (rs.getString("last_name"));
        p.setUniformName(rs.getString("uniform_name"));
        p.setShirtNumber(rs.getInt  ("shirt_number"));
        p.setDominantFoot(rs.getString("dominant_foot"));
        p.setCaptain   (rs.getInt   ("is_captain") == 1);
        // player_role の読み込み
        try {
            String roleStr = rs.getString("player_role");
            if (roleStr != null) {
                model.Player.PlayerRole role = switch (roleStr) {
                    case "BENCH"    -> model.Player.PlayerRole.BENCH;
                    case "INACTIVE" -> model.Player.PlayerRole.INACTIVE;
                    case "ACADEMY"  -> model.Player.PlayerRole.ACADEMY;
                    case "RETIRED"  -> model.Player.PlayerRole.RETIRED;
                    case "PENDING"  -> { p.setPendingPromotion(true);
                                        yield model.Player.PlayerRole.ACADEMY; }
                    default         -> model.Player.PlayerRole.REGISTERED;
                };
                p.setRole(role);
            }
        } catch (SQLException ignored) {}
        // 引退・転生フィールドの読み込み
        try { p.setRetirementAnnounced(rs.getInt("retirement_announced") == 1); }
        catch (SQLException ignored) {}
        try { p.setReincarnationSeason(rs.getInt("reincarnation_season")); }
        catch (SQLException ignored) {}
        try { p.setFormerClubName(rs.getString("former_club_name")); }
        catch (SQLException ignored) {}
        p.setImageFile(rs.getString("image_file"));
        return p;
    }

    /**
     * ExcelポジションコードをGameのPositon enumに変換。
     * CF/LW/RW/SS/ST → FW
     * CMF/OMF/DMF/LSB/RSB → MF or DF
     */
    public static Player.Position mapPosition(String s) {
        if (s == null) return Player.Position.MF;
        return switch (s.toUpperCase().trim()) {
            case "GK"                                 -> Player.Position.GK;
            case "CB","LSB","RSB","LB","RB","DF","SW" -> Player.Position.DF;
            case "CF","ST","SS","LW","RW","FW"        -> Player.Position.FW;
            default                                   -> Player.Position.MF;
        };
    }
}
