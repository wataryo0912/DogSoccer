package model;

import java.util.*;

/**
 * リーグ戦の固定スケジュール管理
 *
 * 1シーズン52週のうち、試合週は事前に決定される。
 * 残りの週はプレイヤーが練習か移籍を自由に選べる。
 *
 * スケジュール生成:
 *   - 対戦クラブ数 n に対してホーム&アウェイで 2*(n-1) 試合
 *   - 試合は第5週〜第50週の範囲に均等配置
 *   - 連続試合週は避ける（1週間は試合後インターバル）
 */
public class FixtureManager {

    public static class Fixture {
        public final int    week;       // 試合が行われる週番号
        public final Club   opponent;   // 対戦相手
        public final boolean isHome;   // ホームかアウェイか

        // 試合結果（試合後に記録）
        public int  homeGoals = -1;
        public int  awayGoals = -1;
        public boolean played = false;

        public Fixture(int week, Club opponent, boolean isHome) {
            this.week     = week;
            this.opponent = opponent;
            this.isHome   = isHome;
        }

        public void recordResult(int hg, int ag) {
            this.homeGoals = hg;
            this.awayGoals = ag;
            this.played    = true;
        }

        public String getResultText() {
            if (!played) return "未";
            int myGoals  = isHome ? homeGoals : awayGoals;
            int oppGoals = isHome ? awayGoals : homeGoals;
            String mark = myGoals > oppGoals ? "✅" : myGoals == oppGoals ? "🟡" : "❌";
            return mark + " " + myGoals + "-" + oppGoals;
        }

        public String getLabel() {
            String ha = isHome ? "🏠 HOME" : "✈️ AWAY";
            return ha + "  vs  " + opponent.getName();
        }
    }

    private final List<Fixture> fixtures = new ArrayList<>();

    /**
     * スケジュールを生成する
     * @param playerClub プレイヤークラブ
     * @param allClubs   全クラブリスト
     */
    public void generate(Club playerClub, List<Club> allClubs) {
        fixtures.clear();
        List<Club> opponents = allClubs.stream()
            .filter(c -> c != playerClub)
            .toList();

        // ホーム&アウェイのペアリスト
        List<Club[]> pairs = new ArrayList<>();
        for (Club opp : opponents) {
            pairs.add(new Club[]{opp, Boolean.TRUE  ? opp : null});  // home
            pairs.add(new Club[]{opp, null});                         // away
        }

        // シャッフル
        Collections.shuffle(pairs, new Random(playerClub.getName().hashCode()));

        // 第5週〜第50週の間に均等配置（連戦を避ける）
        int startWeek = 5;
        int endWeek   = 50;
        int span      = endWeek - startWeek;
        int matchCount = Math.min(pairs.size(), span / 2);

        Set<Integer> usedWeeks = new HashSet<>();
        int assigned = 0;
        Random rnd = new Random(42);

        for (int i = 0; i < pairs.size() && assigned < matchCount; i++) {
            Club   opp    = pairs.get(i)[0];
            boolean isHome = (i % 2 == 0);

            // 空き週を探す（前後1週間はインターバル）
            int week = startWeek + (int)((double)(assigned * span) / matchCount) + rnd.nextInt(3);
            week = Math.max(startWeek, Math.min(endWeek, week));
            while (usedWeeks.contains(week) || usedWeeks.contains(week-1) || usedWeeks.contains(week+1)) {
                week++;
                if (week > endWeek) { week = startWeek; }
            }
            usedWeeks.add(week);
            fixtures.add(new Fixture(week, opp, isHome));
            assigned++;
        }

        // 週順にソート
        fixtures.sort(Comparator.comparingInt(f -> f.week));
    }

    /** 指定週の試合を取得（なければnull） */
    public Fixture getFixtureForWeek(int week) {
        return fixtures.stream().filter(f -> f.week == week).findFirst().orElse(null);
    }

    /** 全スケジュール */
    public List<Fixture> getAll() { return Collections.unmodifiableList(fixtures); }

    /** 未消化の次の試合 */
    public Fixture getNextFixture(int currentWeek) {
        return fixtures.stream()
            .filter(f -> !f.played && f.week >= currentWeek)
            .findFirst().orElse(null);
    }

    /** 試合週かどうか */
    public boolean isMatchWeek(int week) {
        return fixtures.stream().anyMatch(f -> f.week == week);
    }

    /** 消化済み試合数 */
    public long getPlayedCount() {
        return fixtures.stream().filter(f -> f.played).count();
    }
}
