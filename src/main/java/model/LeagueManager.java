package model;

import java.util.*;

/**
 * 3部リーグ全体を管理するシングルトン的マネージャー
 *
 * 【責務】
 *   - 3つのLeagueオブジェクトを保持
 *   - 24チームの初期振り分け
 *   - シーズン終了時の昇格・降格処理
 *   - プレイヤークラブの所属リーグ追跡
 *   - 監督移籍（別クラブへの就任）処理
 */
public class LeagueManager {

    // ── リーグインスタンス ──────────────────────────────────
    private final League tier1 = new League(League.Tier.TIER1);
    private final League tier2 = new League(League.Tier.TIER2);
    private final League tier3 = new League(League.Tier.TIER3);

    private final Map<League.Tier, League> leagueMap = new EnumMap<>(League.Tier.class);

    // ── コンストラクタ ──────────────────────────────────────
    public LeagueManager() {
        leagueMap.put(League.Tier.TIER1, tier1);
        leagueMap.put(League.Tier.TIER2, tier2);
        leagueMap.put(League.Tier.TIER3, tier3);
    }

    // ── 初期化 ──────────────────────────────────────────────

    /**
     * 24チームを3部リーグに振り分ける。
     * allClubs の順番 = 振り分け順（0-7: TIER1, 8-15: TIER2, 16-23: TIER3）
     * playerClub は TIER2[0] に固定。
     */
    public void initialize(List<Club> allClubs) {
        tier1.getClubs().forEach(c -> {}); // clear は removeClub で対応
        // 全リーグをクリア
        new ArrayList<>(tier1.getClubs()).forEach(tier1::removeClub);
        new ArrayList<>(tier2.getClubs()).forEach(tier2::removeClub);
        new ArrayList<>(tier3.getClubs()).forEach(tier3::removeClub);

        for (int i = 0; i < allClubs.size(); i++) {
            Club c = allClubs.get(i);
            if      (i < 8)  tier1.addClub(c);
            else if (i < 16) tier2.addClub(c);
            else             tier3.addClub(c);
        }

        System.out.println("[League] 初期化: "
            + "1部=" + tier1.size() + "  2部=" + tier2.size() + "  3部=" + tier3.size());
    }

    // ── 試合結果の記録 ──────────────────────────────────────

    /**
     * 試合結果をそのクラブが所属するリーグに記録する。
     */
    public void recordMatch(Club home, Club away, int homeGoals, int awayGoals) {
        League league = getLeagueOf(home);
        if (league != null) league.recordMatch(home, away, homeGoals, awayGoals);
    }

    // ── 昇降格処理 ──────────────────────────────────────────

    /**
     * シーズン終了時に昇格・降格を実行する。
     * @return 処理結果の文字列リスト（UIへの表示用）
     */
    public List<String> processPromotionRelegation() {
        List<String> messages = new ArrayList<>();

        // TIER2→TIER1 昇格 & TIER1→TIER2 降格
        processMovement(tier2, tier1, messages);
        // TIER3→TIER2 昇格 & TIER2→TIER3 降格
        processMovement(tier3, tier2, messages);

        // 全リーグの順位表リセット（新シーズン向け）
        tier1.resetStandings();
        tier2.resetStandings();
        tier3.resetStandings();

        return messages;
    }

    private void processMovement(League lower, League upper, List<String> messages) {
        List<Club> promoted  = new ArrayList<>(lower.getPromotionCandidates());
        List<Club> relegated = new ArrayList<>(upper.getRelegationCandidates());

        for (Club club : promoted) {
            lower.removeClub(club);
            upper.addClub(club);
            messages.add("⬆️  【昇格】 " + club.getName()
                + "  " + lower.getTier().getDisplayName()
                + " → " + upper.getTier().getDisplayName());
        }
        for (Club club : relegated) {
            upper.removeClub(club);
            lower.addClub(club);
            messages.add("⬇️  【降格】 " + club.getName()
                + "  " + upper.getTier().getDisplayName()
                + " → " + lower.getTier().getDisplayName());
        }
    }

    // ── 監督移籍 ──────────────────────────────────────────

    /**
     * プレイヤー（監督）を新しいクラブに就任させる。
     * 選手はそのまま元クラブに残る。
     * @param newClub 就任先クラブ
     * @return 就任メッセージ
     */
    public String transferManager(Club newClub) {
        League targetLeague = getLeagueOf(newClub);
        if (targetLeague == null) return "クラブが見つかりません";
        return "✅ " + newClub.getName() + " の監督に就任しました！ ("
            + targetLeague.getTier().getDisplayName() + ")";
    }

    // ── 照会 ──────────────────────────────────────────────

    /** クラブが所属するリーグを返す */
    public League getLeagueOf(Club club) {
        for (League l : leagueMap.values()) {
            if (l.contains(club)) return l;
        }
        return null;
    }

    /** ティアを指定してリーグを取得 */
    public League getLeague(League.Tier tier) {
        return leagueMap.get(tier);
    }

    /** 全リーグをティア順に返す */
    public List<League> getAllLeagues() {
        return List.of(tier1, tier2, tier3);
    }

    /**
     * 監督移籍の候補クラブ一覧を返す。
     * 現在のプレイヤークラブを除く、同一ティアの全クラブ。
     */
    public List<Club> getTransferCandidates(Club currentClub) {
        League current = getLeagueOf(currentClub);
        if (current == null) return List.of();
        return current.getClubs().stream()
            .filter(c -> !c.getName().equals(currentClub.getName()))
            .toList();
    }

    /**
     * 全ティアの全クラブから監督移籍候補を返す（自クラブ除く）。
     * 昇格先・降格先リーグも選べるようにするため。
     */
    public List<Club> getAllTransferCandidates(Club currentClub) {
        List<Club> result = new ArrayList<>();
        for (League l : leagueMap.values()) {
            for (Club c : l.getClubs()) {
                if (!c.getName().equals(currentClub.getName())) {
                    result.add(c);
                }
            }
        }
        return result;
    }

    /** クラブのリーグ内順位を返す */
    public int getRankOf(Club club) {
        League l = getLeagueOf(club);
        return l != null ? l.getRankOf(club) : -1;
    }

    /** クラブのリーグ内順位テキスト（例: "3位 / 8チーム"） */
    public String getRankText(Club club) {
        League l = getLeagueOf(club);
        if (l == null) return "—";
        int rank = l.getRankOf(club);
        return rank + "位 / " + l.size() + "チーム";
    }
}
