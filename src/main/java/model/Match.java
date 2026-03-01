package model;

/**
 * 試合の結果を記録するクラス
 */
public class Match {
    private Club homeClub;
    private Club awayClub;
    private int homeGoals;
    private int awayGoals;
    private boolean played;

    public Match(Club homeClub, Club awayClub) {
        this.homeClub = homeClub;
        this.awayClub = awayClub;
        this.played = false;
    }

    public void setResult(int homeGoals, int awayGoals) {
        this.homeGoals = homeGoals;
        this.awayGoals = awayGoals;
        this.played = true;
        homeClub.recordResult(homeGoals, awayGoals);
        awayClub.recordResult(awayGoals, homeGoals);
    }

    // ゲッター
    public Club getHomeClub() { return homeClub; }
    public Club getAwayClub() { return awayClub; }
    public int getHomeGoals() { return homeGoals; }
    public int getAwayGoals() { return awayGoals; }
    public boolean isPlayed() { return played; }

    @Override
    public String toString() {
        if (!played) return homeClub.getName() + " vs " + awayClub.getName();
        return String.format("%s %d - %d %s",
                homeClub.getName(), homeGoals, awayGoals, awayClub.getName());
    }
}
