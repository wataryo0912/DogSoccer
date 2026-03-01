package service;

import db.PlayerDao;
import model.Club;
import model.Player;
import model.Player.PlayerRole;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 現役引退・転生サービス
 *
 * 【引退ロジック】
 *  - シーズン開始時（week=1）に 30歳以上の全選手を査定
 *  - 年齢ベースの確率テーブルで引退表明フラグを立てる
 *    30歳: 5%  31歳: 10%  32歳: 18%  33歳: 28%
 *    34歳: 38%  35歳: 50%  36歳: 62%  37歳: 73%  38歳以上: 82%
 *  - 引退表明選手はそのシーズン通常プレー可能（ロール変更不可）
 *  - シーズン終了時に RETIRED ロールへ変更し、スカッドから除外
 *
 * 【転生ロジック】
 *  - 引退時に reincarnationSeason = 現在シーズン + 1 or 2 (50:50) を記録
 *  - 対象シーズン到来時、元所属クラブ（同犬種優先）の下部組織に追加
 *  - 転生後の能力: 年齢 16-18歳 / overall 55-68 / potential 72-88
 *  - 名前は「（元の名前）Ⅱ世」（転生らしさを演出）
 */
public class RetirementService {

    private final PlayerDao playerDao  = new PlayerDao();
    private final Random    rng        = new Random();

    // ── 年齢別引退確率テーブル ────────────────────────────────
    private static final Map<Integer, Double> RETIRE_PROB = new LinkedHashMap<>();
    static {
        RETIRE_PROB.put(30, 0.05);
        RETIRE_PROB.put(31, 0.10);
        RETIRE_PROB.put(32, 0.18);
        RETIRE_PROB.put(33, 0.28);
        RETIRE_PROB.put(34, 0.38);
        RETIRE_PROB.put(35, 0.50);
        RETIRE_PROB.put(36, 0.62);
        RETIRE_PROB.put(37, 0.73);
        // 38歳以上は 82%
    }
    private static final double MAX_PROB = 0.82;

    // ── シーズン開始時：引退表明チェック ─────────────────────

    /**
     * シーズン開始時（week=1）に呼ぶ。
     * 30歳以上の選手を査定して引退表明フラグを立てる。
     *
     * @param clubs      全クラブリスト
     * @param currentSeason 現在のシーズン番号
     * @return 引退表明した選手のメッセージリスト（UI表示用）
     */
    public List<RetirementAnnouncement> checkRetirements(
            List<Club> clubs, int currentSeason) {

        List<RetirementAnnouncement> announcements = new ArrayList<>();

        for (Club club : clubs) {
            for (Player p : new ArrayList<>(club.getSquad())) {
                // 既に引退・表明済みはスキップ
                if (p.getRole() == PlayerRole.RETIRED) continue;
                if (p.isRetirementAnnounced()) continue;
                if (p.getAge() < 30) continue;

                double prob = RETIRE_PROB.getOrDefault(p.getAge(), MAX_PROB);
                if (rng.nextDouble() < prob) {
                    // 引退表明
                    p.setRetirementAnnounced(true);
                    // 転生シーズン = 引退確定(currentSeason+1) の 1 or 2シーズン後
                    int reincSeason = (currentSeason + 1) + rng.nextInt(2); // +1 or +2
                    p.setReincarnationSeason(reincSeason);
                    p.setFormerClubName(club.getName());

                    // DB 即時保存
                    if (p.getId() > 0) {
                        try { playerDao.updateRetirement(p); }
                        catch (SQLException e) {
                            System.err.println("[Retirement] DB保存エラー: " + e.getMessage());
                        }
                    }

                    announcements.add(new RetirementAnnouncement(p, club));
                    System.out.printf("[Retirement] 引退表明: %s (%d歳) @ %s → 転生S%d%n",
                        p.getFullName(), p.getAge(), club.getName(), reincSeason);
                }
            }
        }
        return announcements;
    }

    // ── シーズン終了時：引退確定処理 ─────────────────────────

    /**
     * シーズン終了時に呼ぶ。引退表明済み選手を RETIRED へ変更し
     * スカッドから除外する。
     *
     * @return 引退確定した選手リスト（SeasonEndView 用）
     */
    public List<Player> processRetirements(List<Club> clubs) {
        List<Player> retired = new ArrayList<>();
        for (Club club : clubs) {
            List<Player> toRetire = club.getSquad().stream()
                .filter(Player::isRetirementAnnounced)
                .collect(Collectors.toList());

            for (Player p : toRetire) {
                p.setRole(PlayerRole.RETIRED);
                p.setRetirementAnnounced(false); // 処理完了フラグ解除
                club.getSquad().remove(p);
                retired.add(p);

                if (p.getId() > 0) {
                    try { playerDao.updateRetirement(p); }
                    catch (SQLException e) {
                        System.err.println("[Retirement] 引退確定DB保存エラー: " + e.getMessage());
                    }
                }
                System.out.printf("[Retirement] 引退確定: %s (転生S%d)%n",
                    p.getFullName(), p.getReincarnationSeason());
            }
        }
        return retired;
    }

    // ── シーズン開始時：転生チェック ─────────────────────────

    /**
     * シーズン開始時に呼ぶ。転生シーズンが来た RETIRED 選手を
     * ゆかりのクラブ（同犬種 or 元所属）の下部組織に ACADEMY として追加する。
     *
     * @param clubs         全クラブリスト
     * @param currentSeason 新しいシーズン番号
     * @return 転生した選手のメッセージリスト
     */
    public List<ReincarnationResult> processReincarnations(
            List<Club> clubs, int currentSeason) {

        List<ReincarnationResult> results = new ArrayList<>();

        // DB から転生対象を取得（全クラブを超えて存在する可能性あり）
        List<Player> candidates;
        try {
            candidates = playerDao.findPendingReincarnations(currentSeason);
        } catch (SQLException e) {
            System.err.println("[Reincarnation] DB取得エラー: " + e.getMessage());
            return results;
        }

        for (Player original : candidates) {
            Club targetClub = findReincarnationClub(original, clubs);
            if (targetClub == null) continue;

            Player reborn = createReincarnatedPlayer(original);
            targetClub.getSquad().add(reborn);

            // DB に新選手として保存
            try {
                playerDao.insert(reborn, targetClub.getId());
                // 元のレコードの転生シーズンをリセット（再転生防止）
                original.setReincarnationSeason(-2); // -2 = 転生済み
                playerDao.updateRetirement(original);
            } catch (SQLException e) {
                System.err.println("[Reincarnation] DB保存エラー: " + e.getMessage());
            }

            results.add(new ReincarnationResult(original, reborn, targetClub));
            System.out.printf("[Reincarnation] 転生: %s → %s @ %s (下部組織)%n",
                original.getFullName(), reborn.getFullName(), targetClub.getName());
        }
        return results;
    }

    // ── 転生先クラブの決定 ────────────────────────────────────

    /**
     * 転生先クラブを決定する。
     * 優先順位:
     *   1. 元所属クラブ（formerClubName が一致）
     *   2. 同じ犬種のクラブ
     *   3. ランダムに1クラブ
     */
    private Club findReincarnationClub(Player original, List<Club> clubs) {
        if (clubs.isEmpty()) return null;

        // 1. 元所属クラブ
        String former = original.getFormerClubName();
        if (former != null && !former.isEmpty()) {
            for (Club c : clubs) {
                if (c.getName().equals(former)) return c;
            }
        }
        // 2. 同犬種クラブ
        String breed = original.getBreed();
        List<Club> sameBreed = clubs.stream()
            .filter(c -> c.getBreed() != null && c.getBreed().equals(breed))
            .collect(Collectors.toList());
        if (!sameBreed.isEmpty()) {
            return sameBreed.get(rng.nextInt(sameBreed.size()));
        }
        // 3. ランダム
        return clubs.get(rng.nextInt(clubs.size()));
    }

    // ── 転生選手の生成 ────────────────────────────────────────

    /**
     * 転生した新選手を生成する。
     * - 名前: 「元の名前 Ⅱ世」
     * - 年齢: 16-18歳
     * - overall: 55-68（能力標準化）
     * - potential: 72-88（伸び代あり）
     * - 能力値: overall に合わせて自動生成
     * - ロール: ACADEMY
     * - formerClubName: 元の選手名を記録
     */
    private Player createReincarnatedPlayer(Player original) {
        int age     = 16 + rng.nextInt(3);           // 16-18
        int overall = 55 + rng.nextInt(14);           // 55-68
        int pot     = 72 + rng.nextInt(17);           // 72-88

        // 能力上限を補正
        pot = Math.min(pot, 99);
        overall = Math.min(overall, pot - 5);

        long salary = 300_000L + rng.nextInt(200_000);
        long mv     = 2_000_000L + rng.nextInt(3_000_000);

        Player reborn = new Player(
            original.getName() + " Ⅱ世",
            age, "犬", original.getBreed(),
            original.getPosition(),
            overall, pot,
            salary, mv, 3
        );
        reborn.setLastName(original.getLastName());
        reborn.setUniformName(original.getUniformName() + "2");
        reborn.setDominantFoot(original.getDominantFoot());
        reborn.setRole(PlayerRole.ACADEMY);
        // 転生元を記録
        reborn.setFormerClubName(original.getFullName() + "（転生）");
        // 転生シーズンは不要なのでリセット
        reborn.setReincarnationSeason(-1);

        return reborn;
    }

    // ── データクラス ──────────────────────────────────────────

    /** 引退表明のデータ（UI通知用） */
    public record RetirementAnnouncement(Player player, Club club) {
        public String toMessage() {
            return String.format(
                "🐾 %s（%d歳 / %s）が今シーズン限りでの引退を表明しました。\n"
                + "今シーズンは引き続き出場できます。\n"
                + "転生予定: 第%dシーズン @%s 下部組織",
                player.getFullName(), player.getAge(),
                player.getBreed(),
                player.getReincarnationSeason(),
                club.getName()
            );
        }
        public String toShortMessage() {
            return String.format("%s（%d歳）引退表明 → S%d転生予定",
                player.getFullName(), player.getAge(),
                player.getReincarnationSeason());
        }
    }

    /** 転生完了のデータ（UI通知用） */
    public record ReincarnationResult(Player original, Player reborn, Club club) {
        public String toMessage() {
            return String.format(
                "✨ %s が %s として %s の下部組織に転生しました！\n"
                + "年齢: %d歳 / OVR: %d / POT: %d",
                original.getFullName(),
                reborn.getFullName(),
                club.getName(),
                reborn.getAge(),
                reborn.getOverall(),
                reborn.getPotential()
            );
        }
    }

    // ── ユーティリティ ────────────────────────────────────────

    /** 引退表明中の選手を見やすく整形して返す */
    public List<String> getAnnouncedRetirements(Club club) {
        return club.getSquad().stream()
            .filter(Player::isRetirementAnnounced)
            .map(p -> String.format("⌛ %s（%d歳 / %s）",
                p.getFullName(), p.getAge(), p.getPosition()))
            .collect(Collectors.toList());
    }
}
