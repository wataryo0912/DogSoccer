package model;

/**
 * シーズンの固定スケジュール1試合分
 * SeasonManager が initSchedule() で52週分生成して保持する。
 */
public class ScheduledMatch {

    private final int    week;         // 何週目か（1〜52）
    private final String homeClubName; // ホームチーム名
    private final String awayClubName; // アウェイチーム名
    private boolean      played;       // 試合済みか
    private int          homeGoals = -1;
    private int          awayGoals = -1;

    public ScheduledMatch(int week, String homeClubName, String awayClubName) {
        this.week          = week;
        this.homeClubName  = homeClubName;
        this.awayClubName  = awayClubName;
        this.played        = false;
    }

    public void recordResult(int hg, int ag) {
        this.homeGoals = hg;
        this.awayGoals = ag;
        this.played    = true;
    }

    public boolean isPlayerMatch(String playerClubName) {
        return homeClubName.equals(playerClubName) || awayClubName.equals(playerClubName);
    }

    public String getOpponentName(String playerClubName) {
        return homeClubName.equals(playerClubName) ? awayClubName : homeClubName;
    }

    public boolean isPlayerHome(String playerClubName) {
        return homeClubName.equals(playerClubName);
    }

    // ── Getters ──────────────────────────────────────────────
    public int    getWeek()          { return week; }
    public String getHomeClubName()  { return homeClubName; }
    public String getAwayClubName()  { return awayClubName; }
    public boolean isPlayed()        { return played; }
    public int    getHomeGoals()     { return homeGoals; }
    public int    getAwayGoals()     { return awayGoals; }

    public String getScoreText() {
        return played ? homeGoals + " - " + awayGoals : "vs";
    }

    /** スケジュール表示用テキスト */
    public String getSummary() {
        String score = played
            ? String.format(" [%d-%d]", homeGoals, awayGoals)
            : "";
        return String.format("第%d週  %s vs %s%s",
            week, homeClubName, awayClubName, score);
    }
}
