package model;

/**
 * 試合中の1イベント
 * SPECIAL_MOVE タイプが追加され、発動選手と必殺技情報を保持する。
 */
public class MatchEvent {

    public enum Type {
        KICKOFF, HALFTIME, FULLTIME,
        GOAL, SHOT, SHOT_ON, SAVE, MISS,
        CHANCE, COUNTER, FOUL, CORNER, PASS, PRESS,
        SPECIAL_MOVE   // ← 必殺技発動イベント
    }

    private final int    minute;
    private final Type   type;
    private final String message;
    private final boolean isHome;
    private final double ballX;
    private final double ballY;
    private final int    homeScore;
    private final int    awayScore;
    private final int    homePoss;
    private final int[]  shots;
    private final int[]  shotsOn;
    private final int[]  corners;
    private final int[]  fouls;

    // ── 選手情報（背番号表示・必殺技用） ─────────────────────
    private final Player     triggerPlayer;   // イベントを起こした選手（nullの場合あり）
    private final SpecialMove specialMove;    // 発動した必殺技（SPECIAL_MOVEタイプ時のみ非null）

    // ── フル引数コンストラクタ ────────────────────────────────
    public MatchEvent(int minute, Type type, String message, boolean isHome,
                      double ballX, double ballY,
                      int homeScore, int awayScore, int homePoss,
                      int[] shots, int[] shotsOn, int[] corners, int[] fouls,
                      Player triggerPlayer, SpecialMove specialMove) {
        this.minute        = minute;
        this.type          = type;
        this.message       = message;
        this.isHome        = isHome;
        this.ballX         = ballX;
        this.ballY         = ballY;
        this.homeScore     = homeScore;
        this.awayScore     = awayScore;
        this.homePoss      = homePoss;
        this.shots         = shots.clone();
        this.shotsOn       = shotsOn.clone();
        this.corners       = corners.clone();
        this.fouls         = fouls.clone();
        this.triggerPlayer = triggerPlayer;
        this.specialMove   = specialMove;
    }

    /** 後方互換コンストラクタ（選手・必殺技なし） */
    public MatchEvent(int minute, Type type, String message, boolean isHome,
                      double ballX, double ballY,
                      int homeScore, int awayScore, int homePoss,
                      int[] shots, int[] shotsOn, int[] corners, int[] fouls) {
        this(minute, type, message, isHome, ballX, ballY,
             homeScore, awayScore, homePoss,
             shots, shotsOn, corners, fouls, null, null);
    }

    // ── Getters ──────────────────────────────────────────────
    public int        getMinute()       { return minute; }
    public Type       getType()         { return type; }
    public String     getMessage()      { return message; }
    public boolean    isHome()          { return isHome; }
    public double     getBallX()        { return ballX; }
    public double     getBallY()        { return ballY; }
    public int        getHomeScore()    { return homeScore; }
    public int        getAwayScore()    { return awayScore; }
    public int        getHomePoss()     { return homePoss; }
    public int[]      getShots()        { return shots; }
    public int[]      getShotsOn()      { return shotsOn; }
    public int[]      getCorners()      { return corners; }
    public int[]      getFouls()        { return fouls; }
    public Player     getTriggerPlayer(){ return triggerPlayer; }
    public SpecialMove getSpecialMove() { return specialMove; }
    public boolean    hasSpecialMove()  { return specialMove != null; }
}
