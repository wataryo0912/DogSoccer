package model;

import java.util.*;

/**
 * シーズン進行管理モデル
 *
 * 1シーズン = 52週（1年）
 * シーズン開始時に initSchedule() で全試合スケジュールを生成。
 * 試合週(MATCH_WEEKS)は自動的に試合が行われ、アクション選択は不要。
 */
public class SeasonManager {

    public enum Action   { TRAINING, MATCH, TRANSFER, NONE }
    public enum Season   { SPRING, SUMMER, AUTUMN, WINTER }

    public static class WeekRecord {
        public final int    week;
        public final Action action;
        public final String result;
        public final long   budgetAfter;

        public WeekRecord(int week, Action action, String result, long budgetAfter) {
            this.week        = week;
            this.action      = action;
            this.result      = result;
            this.budgetAfter = budgetAfter;
        }

        public String getActionLabel() {
            return switch (action) {
                case TRAINING  -> "🏋️ 練習";
                case MATCH     -> "⚽ 試合";
                case TRANSFER  -> "🔄 移籍";
                case NONE      -> "─";
            };
        }
    }

    // ── 状態 ─────────────────────────────────────────────────
    private int  currentWeek   = 1;
    private int  currentSeason = 1;
    private boolean seasonOver = false;

    private final List<WeekRecord>      history  = new ArrayList<>();
    private final List<ScheduledMatch>  schedule = new ArrayList<>();

    // ── 定数 ─────────────────────────────────────────────────
    public static final int TOTAL_WEEKS = 52;

    /** 移籍ウィンドウが開いている週（冬:1-4、夏:27-30） */
    public static final Set<Integer> TRANSFER_WINDOWS =
        new HashSet<>(Arrays.asList(1, 2, 3, 4, 27, 28, 29, 30));

    // ── スケジュール初期化 ────────────────────────────────────

    /**
     * シーズン開始時に試合スケジュールを生成する。
     * リーグ方式: 全クラブと1回ずつ対戦（ホーム/アウェイ）
     * 試合は移籍ウィンドウ外・均等間隔で配置。
     */
    public void initSchedule(List<Club> allClubs, String playerClubName) {
        schedule.clear();

        // 対戦カード生成（ホーム&アウェイで全組み合わせ）
        List<String[]> fixtures = new ArrayList<>();
        List<String> names = allClubs.stream().map(Club::getName).toList();
        for (int i = 0; i < names.size(); i++) {
            for (int j = i + 1; j < names.size(); j++) {
                fixtures.add(new String[]{names.get(i), names.get(j)});
                fixtures.add(new String[]{names.get(j), names.get(i)});
            }
        }
        Collections.shuffle(fixtures, new Random(currentSeason));  // シーズンごとに順序変化

        // 試合週の候補（移籍ウィンドウ外、5週おきに試合を配置）
        List<Integer> matchWeeks = new ArrayList<>();
        for (int w = 5; w <= TOTAL_WEEKS - 2; w++) {
            if (!TRANSFER_WINDOWS.contains(w)) matchWeeks.add(w);
        }
        // プレイヤークラブの試合のみスケジュール（最大12試合）
        List<String[]> playerFixtures = fixtures.stream()
            .filter(f -> f[0].equals(playerClubName) || f[1].equals(playerClubName))
            .limit(12)
            .toList();

        int weekIdx = 0;
        for (String[] f : playerFixtures) {
            if (weekIdx >= matchWeeks.size()) break;
            schedule.add(new ScheduledMatch(matchWeeks.get(weekIdx), f[0], f[1]));
            weekIdx += 4;  // 4週間隔
        }

        // ソート
        schedule.sort(Comparator.comparingInt(ScheduledMatch::getWeek));
        System.out.println("[Schedule] " + schedule.size() + "試合をスケジュール");
        schedule.forEach(s -> System.out.println("  " + s.getSummary()));
    }

    /** 今週に試合があるか */
    public ScheduledMatch getMatchForCurrentWeek() {
        return schedule.stream()
            .filter(s -> s.getWeek() == currentWeek && !s.isPlayed())
            .findFirst().orElse(null);
    }

    /** 今週は試合週か */
    public boolean isMatchWeek() {
        return getMatchForCurrentWeek() != null;
    }

    public List<ScheduledMatch> getSchedule() { return schedule; }

    /** 今後の試合スケジュール（未消化のみ） */
    public List<ScheduledMatch> getUpcomingMatches() {
        return schedule.stream()
            .filter(s -> s.getWeek() >= currentWeek)
            .toList();
    }

    // ── ゲッター ─────────────────────────────────────────────
    public int     getCurrentWeek()   { return currentWeek; }
    public int     getCurrentSeason() { return currentSeason; }
    public boolean isSeasonOver()     { return seasonOver; }
    public List<WeekRecord> getHistory() { return history; }

    public int getCurrentMonth() {
        return ((currentWeek - 1) / 4) + 1;
    }

    public Season getCurrentSeason_() {
        int m = getCurrentMonth();
        if (m <= 3)  return Season.WINTER;
        if (m <= 6)  return Season.SPRING;
        if (m <= 9)  return Season.SUMMER;
        return Season.AUTUMN;
    }

    public boolean isTransferWindowOpen() {
        return TRANSFER_WINDOWS.contains(currentWeek);
    }

    public String getWeekTitle() {
        int m = getCurrentMonth();
        int w = ((currentWeek - 1) % 4) + 1;
        return String.format("第%d週  %d月第%d週", currentWeek, m, w);
    }

    public double getProgress() {
        return (double)(currentWeek - 1) / TOTAL_WEEKS;
    }

    public int getRemainingWeeks() {
        return TOTAL_WEEKS - currentWeek + 1;
    }

    public boolean canDoAction(Action action) {
        if (seasonOver) return false;
        if (isMatchWeek()) return false;  // 試合週は他アクション不可
        if (action == Action.TRANSFER && !isTransferWindowOpen()) return false;
        return true;
    }

    public int advanceWeek(Action action, String result, long budgetAfter) {
        history.add(new WeekRecord(currentWeek, action, result, budgetAfter));
        currentWeek++;
        if (currentWeek > TOTAL_WEEKS) {
            seasonOver = true;
            currentWeek = TOTAL_WEEKS;
        }
        return currentWeek;
    }

    // ── シーズン開始フック ────────────────────────────────────
    /** シーズン開始時に呼ばれるコールバック（引退・転生処理用） */
    private java.util.function.Consumer<Integer> onSeasonStartHook = null;
    public void setOnSeasonStartHook(java.util.function.Consumer<Integer> hook) {
        this.onSeasonStartHook = hook;
    }

    public void startNewSeason(List<Club> allClubs, String playerClubName) {
        currentSeason++;
        currentWeek = 1;
        seasonOver  = false;
        history.clear();
        // 全クラブの成績をリセット
        allClubs.forEach(c -> {
            c.resetRecord();
            c.getSquad().forEach(p -> {
                p.setGoals(0);
                p.setAssists(0);
            });
        });
        initSchedule(allClubs, playerClubName);
        // シーズン開始フック（引退チェック・転生処理）
        if (onSeasonStartHook != null) {
            onSeasonStartHook.accept(currentSeason);
        }
    }

    public WeekRecord getRecordForWeek(int week) {
        return history.stream()
            .filter(r -> r.week == week)
            .findFirst().orElse(null);
    }

    public String getSeasonSummary(Club club) {
        long trainCount = history.stream().filter(r -> r.action == Action.TRAINING).count();
        long matchCount = history.stream().filter(r -> r.action == Action.MATCH).count();
        long transCount = history.stream().filter(r -> r.action == Action.TRANSFER).count();
        return String.format(
            "第%dシーズン終了\n練習:%d回  試合:%d回  移籍:%d回\n最終成績: %dW-%dD-%dL (%dpt)",
            currentSeason, trainCount, matchCount, transCount,
            club.getWins(), club.getDraws(), club.getLosses(), club.getPoints());
    }
}
