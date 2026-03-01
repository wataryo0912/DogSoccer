package model;

import java.util.*;

/**
 * リーグエンティティ
 *
 * ┌────────────────────────────────────────┐
 * │  Tier 1: 犬リーグ 1部  (強豪 8チーム)  │
 * │  Tier 2: 犬リーグ 2部  (中堅 8チーム)  │  ← ぶん助の初期所属
 * │  Tier 3: 犬リーグ 3部  (育成 8チーム)  │
 * └────────────────────────────────────────┘
 *
 * 昇格: 各リーグ上位2チーム → 上のリーグへ
 * 降格: 各リーグ下位2チーム → 下のリーグへ
 */
public class League {

    public static final int TEAMS_PER_LEAGUE   = 8;
    public static final int PROMOTION_SLOTS    = 2;   // 昇格枠
    public static final int RELEGATION_SLOTS   = 2;   // 降格枠

    // ── リーグ定義 ──────────────────────────────────────────
    public enum Tier {
        TIER1("🥇 犬リーグ 1部", 1),
        TIER2("🥈 犬リーグ 2部", 2),
        TIER3("🥉 犬リーグ 3部", 3);

        private final String displayName;
        private final int    level;

        Tier(String displayName, int level) {
            this.displayName = displayName;
            this.level       = level;
        }

        public String getDisplayName() { return displayName; }
        public int    getLevel()       { return level; }

        public Tier upper() {
            return switch (this) {
                case TIER2 -> TIER1;
                case TIER3 -> TIER2;
                default    -> null;   // TIER1に上はない
            };
        }

        public Tier lower() {
            return switch (this) {
                case TIER1 -> TIER2;
                case TIER2 -> TIER3;
                default    -> null;   // TIER3に下はない
            };
        }
    }

    // ── フィールド ──────────────────────────────────────────
    private final Tier        tier;
    private final List<Club>  clubs     = new ArrayList<>();

    // 順位表エントリ（シーズン中に蓄積）
    public static class Standing {
        public final Club club;
        public int played, wins, draws, losses, goalsFor, goalsAgainst;

        public Standing(Club club) { this.club = club; }

        public int getPoints()  { return wins * 3 + draws; }
        public int getGD()      { return goalsFor - goalsAgainst; }

        /** 勝ち点 → 得失点差 → 得点 の順で比較 */
        public int compareTo(Standing o) {
            if (getPoints() != o.getPoints()) return o.getPoints() - getPoints();
            if (getGD()     != o.getGD())     return o.getGD()     - getGD();
            return o.goalsFor - goalsFor;
        }

        public String getRankLabel(int rank) {
            return String.format("%2d位  %-18s  %2d試 %2dW %2dD %2dL  GD%+3d  %2dpt",
                rank, club.getName(), played, wins, draws, losses, getGD(), getPoints());
        }
    }

    private final Map<String, Standing> standings = new LinkedHashMap<>();

    // ── コンストラクタ ──────────────────────────────────────
    public League(Tier tier) {
        this.tier = tier;
    }

    // ── クラブ管理 ──────────────────────────────────────────
    public void addClub(Club club) {
        if (!clubs.contains(club)) {
            clubs.add(club);
            standings.put(club.getName(), new Standing(club));
        }
    }

    public void removeClub(Club club) {
        clubs.remove(club);
        standings.remove(club.getName());
    }

    public boolean contains(Club club) {
        return clubs.stream().anyMatch(c -> c.getName().equals(club.getName()));
    }

    // ── 試合結果の記録 ──────────────────────────────────────
    public void recordMatch(Club home, Club away, int homeGoals, int awayGoals) {
        Standing hs = standings.get(home.getName());
        Standing as = standings.get(away.getName());
        if (hs == null || as == null) return;

        hs.played++;       as.played++;
        hs.goalsFor     += homeGoals;  hs.goalsAgainst += awayGoals;
        as.goalsFor     += awayGoals;  as.goalsAgainst += homeGoals;

        if (homeGoals > awayGoals)       { hs.wins++;  as.losses++; }
        else if (homeGoals == awayGoals) { hs.draws++; as.draws++;  }
        else                             { hs.losses++;as.wins++;   }
    }

    // ── 順位表取得（ソート済み） ─────────────────────────────
    public List<Standing> getSortedStandings() {
        List<Standing> list = new ArrayList<>(standings.values());
        list.sort(Standing::compareTo);
        return list;
    }

    /** 昇格対象クラブ（上位2チーム）。TIER1は昇格なし */
    public List<Club> getPromotionCandidates() {
        if (tier == Tier.TIER1) return List.of();
        List<Standing> sorted = getSortedStandings();
        return sorted.subList(0, Math.min(PROMOTION_SLOTS, sorted.size()))
                     .stream().map(s -> s.club).toList();
    }

    /** 降格対象クラブ（下位2チーム）。TIER3は降格なし */
    public List<Club> getRelegationCandidates() {
        if (tier == Tier.TIER3) return List.of();
        List<Standing> sorted = getSortedStandings();
        int from = Math.max(0, sorted.size() - RELEGATION_SLOTS);
        return sorted.subList(from, sorted.size())
                     .stream().map(s -> s.club).toList();
    }

    /** 順位を返す（1始まり）。所属していない場合は -1 */
    public int getRankOf(Club club) {
        List<Standing> sorted = getSortedStandings();
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).club.getName().equals(club.getName())) return i + 1;
        }
        return -1;
    }

    /** シーズン開始時に順位表をリセット */
    public void resetStandings() {
        standings.values().forEach(s -> {
            s.played = s.wins = s.draws = s.losses = s.goalsFor = s.goalsAgainst = 0;
        });
    }

    // ── Getter ──────────────────────────────────────────────
    public Tier        getTier()   { return tier; }
    public List<Club>  getClubs()  { return Collections.unmodifiableList(clubs); }
    public Standing    getStanding(Club club) { return standings.get(club.getName()); }
    public int         size()      { return clubs.size(); }
}
