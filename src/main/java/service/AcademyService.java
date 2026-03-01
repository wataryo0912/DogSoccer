package service;

import db.PlayerDao;
import model.Club;
import model.Player;
import model.Player.PlayerRole;

import java.sql.SQLException;
import java.util.*;

/**
 * 下部組織（Academy）管理サービス
 *
 * 【責務】
 *  - 下部組織選手の生成・シード
 *  - 昇格申請の受付（翌週フラグを立てる）
 *  - 週進行時の昇格確定処理（ACADEMY → REGISTERED）
 *  - 役割変更（REGISTERED ↔ BENCH ↔ INACTIVE ↔ ACADEMY）
 *  - キャプテン変更（1クラブ1人制約をDB側にも反映）
 */
public class AcademyService {

    private final PlayerDao playerDao = new PlayerDao();

    // ── 下部組織の初期シード ─────────────────────────────────

    /**
     * クラブに下部組織選手を追加する。
     * 既に ACADEMY 選手がいれば何もしない。
     */
    public void seedAcademyPlayers(Club club) {
        boolean hasAcademy = club.getSquad().stream()
            .anyMatch(p -> p.getRole() == PlayerRole.ACADEMY);
        if (hasAcademy) return;

        String breed = club.getBreed() != null ? club.getBreed() : "柴犬";
        String[][] seeds = {
            {"アカデミー太郎", "GK",  "17", "62", "78"},
            {"育成二郎",       "DF",  "16", "60", "80"},
            {"将来三郎",       "MF",  "17", "63", "82"},
            {"希望四郎",       "FW",  "16", "58", "79"},
            {"新星五郎",       "MF",  "18", "65", "83"},
        };

        Random rng = new Random();
        for (String[] s : seeds) {
            Player p = new Player(
                s[0], Integer.parseInt(s[2]), "犬", breed,
                PlayerDao.mapPosition(s[1]),
                Integer.parseInt(s[3]),   // overall
                Integer.parseInt(s[4]),   // potential
                500_000L + rng.nextInt(200_000),   // 週給
                3_000_000L + rng.nextInt(2_000_000), // 市場価値
                3
            );
            p.setUniformName(s[0].toUpperCase());
            p.setRole(PlayerRole.ACADEMY);
            club.getSquad().add(p);
        }
        System.out.println("[Academy] " + club.getName() + " に下部組織選手5名を追加");
    }

    // ── 昇格申請 ────────────────────────────────────────────

    /**
     * 下部組織選手に「翌週昇格」フラグを立てる。
     * DB にも即時反映する（PENDING ロールで保存）。
     *
     * @return 成功メッセージ
     */
    public String requestPromotion(Club club, Player player) {
        if (player.getRole() != PlayerRole.ACADEMY) {
            return "⚠️ " + player.getFullName() + " は下部組織の選手ではありません";
        }
        if (player.isPendingPromotion()) {
            return "ℹ️ " + player.getFullName() + " は既に昇格申請済みです";
        }

        boolean ok = club.requestPromotion(player);
        if (!ok) return "⚠️ 昇格申請に失敗しました";

        // DB に PENDING として保存
        if (player.getId() > 0) {
            try {
                playerDao.markPendingPromotion(player.getId(), true);
            } catch (SQLException e) {
                System.err.println("[Academy] DB更新エラー: " + e.getMessage());
            }
        }
        return "✅ " + player.getFullName()
            + " の昇格申請を受理しました。来週からスカッドに登録されます！";
    }

    /**
     * 昇格申請をキャンセルする。
     */
    public String cancelPromotion(Club club, Player player) {
        if (!player.isPendingPromotion()) {
            return "ℹ️ " + player.getFullName() + " に昇格申請はありません";
        }
        player.setPendingPromotion(false);
        if (player.getId() > 0) {
            try {
                playerDao.markPendingPromotion(player.getId(), false);
            } catch (SQLException e) {
                System.err.println("[Academy] DB更新エラー: " + e.getMessage());
            }
        }
        return "↩️ " + player.getFullName() + " の昇格申請をキャンセルしました";
    }

    // ── 週進行時の昇格確定処理 ──────────────────────────────

    /**
     * advanceWeek() 後に呼ぶ。保留中の昇格を全て確定する。
     * @return 昇格した選手名のリスト
     */
    public List<String> processPromotions(Club club) {
        List<Player> promoted = club.processPromotions();
        for (Player p : promoted) {
            if (p.getId() > 0) {
                try {
                    playerDao.updateRole(p.getId(), PlayerRole.REGISTERED);
                } catch (SQLException e) {
                    System.err.println("[Academy] 昇格DB保存エラー: " + e.getMessage());
                }
            }
            System.out.println("[Academy] 昇格確定: " + p.getFullName());
        }
        return promoted.stream().map(p ->
            "🎉 " + p.getFullName() + " が下部組織から昇格しました！").toList();
    }

    // ── 役割変更 ────────────────────────────────────────────

    /**
     * 選手の役割を変更し、DBに反映する。
     * ACADEMY への降格は昇格申請フラグもリセットする。
     */
    public String changeRole(Player player, PlayerRole newRole) {
        PlayerRole old = player.getRole();
        if (old == newRole) return player.getFullName() + " はすでに " + newRole.label + " です";

        player.setRole(newRole);
        if (newRole == PlayerRole.ACADEMY || newRole != PlayerRole.ACADEMY) {
            player.setPendingPromotion(false);  // ロール変更時はフラグリセット
        }

        if (player.getId() > 0) {
            try {
                playerDao.updateRole(player.getId(), newRole);
            } catch (SQLException e) {
                System.err.println("[Academy] 役割変更DB保存エラー: " + e.getMessage());
            }
        }
        return String.format("✅ %s  %s → %s",
            player.getFullName(), old.label, newRole.label);
    }

    // ── キャプテン変更 ──────────────────────────────────────

    /**
     * キャプテンを変更する（1クラブ1人制約）。
     * Club.setCaptain() でメモリ上を更新し、PlayerDao.enforceSingleCaptain() でDB反映。
     */
    public String changeCaptain(Club club, Player newCaptain) {
        Player old = club.getCaptain();
        club.setCaptain(newCaptain);  // 既存キャプテン解除 + 新キャプテン設定

        if (club.getId() > 0 && newCaptain.getId() > 0) {
            try {
                playerDao.enforceSingleCaptain(club.getId(), newCaptain.getId());
            } catch (SQLException e) {
                System.err.println("[Academy] キャプテンDB保存エラー: " + e.getMessage());
            }
        }

        String oldName = old != null ? old.getFullName() : "なし";
        return String.format("©️ キャプテン変更: %s → %s",
            oldName, newCaptain.getFullName());
    }
}
