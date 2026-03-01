package db;

import java.sql.*;

/**
 * 移籍履歴のDB操作（MySQL / SQLite 両対応）
 */
public class TransferDao {

    private final DatabaseManager db = DatabaseManager.getInstance();

    /** 移籍を記録する */
    public int insert(int playerId, Integer fromClubId, Integer toClubId,
                      long fee) throws SQLException {
        String sql = """
            INSERT INTO transfers (player_id, from_club_id, to_club_id, transfer_fee)
            VALUES (?, ?, ?, ?)
        """;
        try (PreparedStatement ps = db.getConnection()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt (1, playerId);
            if (fromClubId != null) ps.setInt(2, fromClubId);
            else                    ps.setNull(2, Types.INTEGER);
            if (toClubId != null)   ps.setInt(3, toClubId);
            else                    ps.setNull(3, Types.INTEGER);
            ps.setLong(4, fee);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            return rs.next() ? rs.getInt(1) : -1;
        }
    }
}
