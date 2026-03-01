package model;

/**
 * 2選手間の仲良し度エンティティ
 *
 * ┌──────┬──────────┬─────────────────────────────────────────┐
 * │ Level│ 表示名   │ 能力ボーナス（試合中・同フォーメーション） │
 * ├──────┼──────────┼─────────────────────────────────────────┤
 * │ NONE │ ─        │ なし                                    │
 * │ SMALL│ 🐾 小    │ speed/stamina +2                        │
 * │MEDIUM│ 💛 中    │ speed/stamina +4, shooting/passing +2   │
 * │ LARGE│ ❤️ 大    │ 全能力値 +5                              │
 * └──────┴──────────┴─────────────────────────────────────────┘
 *
 * ポイント → Level の変換:
 *   0-9 : NONE
 *  10-29 : SMALL
 *  30-59 : MEDIUM
 *  60+   : LARGE
 */
public class Friendship {

    // ── 仲良し度レベル ──────────────────────────────────────
    public enum Level {
        NONE  ("─",    0,   0),
        SMALL ("🐾 小", 10,  2),
        MEDIUM("💛 中", 30,  4),
        LARGE ("❤️ 大", 60,  5);

        /** 表示名 */
        public final String label;
        /** このレベルに達するための最低ポイント */
        public final int    threshold;
        /** 試合中の基本ボーナス値 */
        public final int    bonus;

        Level(String label, int threshold, int bonus) {
            this.label     = label;
            this.threshold = threshold;
            this.bonus     = bonus;
        }

        /** ポイントからレベルを算出 */
        public static Level of(int points) {
            if (points >= LARGE.threshold)  return LARGE;
            if (points >= MEDIUM.threshold) return MEDIUM;
            if (points >= SMALL.threshold)  return SMALL;
            return NONE;
        }
    }

    // ── ポイント加算定義 ──────────────────────────────────
    public static final int PTS_MATCH        = 3;   // 試合で同じフィールド
    public static final int PTS_TRAINING     = 2;   // 練習で一緒
    public static final int PTS_EVENT_TARGET = 4;   // イベントで同じ対象・同チーム
    public static final int PTS_SAME_BREED   = 2;   // 同じ犬種ボーナス（加算時に上乗せ）
    public static final int PTS_SAME_POS     = 1;   // 同じポジションボーナス

    // ── フィールド ────────────────────────────────────────
    private final int    playerIdA;   // 小さい方のID（A < B を保証）
    private final int    playerIdB;
    private       int    points;      // 累積ポイント
    private       Level  level;

    // ── コンストラクタ ────────────────────────────────────
    public Friendship(int idA, int idB, int points) {
        // 常に小さいIDをAに揃えることでペアの一意性を保証
        this.playerIdA = Math.min(idA, idB);
        this.playerIdB = Math.max(idA, idB);
        this.points    = points;
        this.level     = Level.of(points);
    }

    public Friendship(int idA, int idB) {
        this(idA, idB, 0);
    }

    // ── ポイント追加 ──────────────────────────────────────
    /**
     * ポイントを加算し、レベルを再計算する。
     * @param delta 加算量（正の値）
     * @return レベルが上がったなら true
     */
    public boolean addPoints(int delta) {
        Level before = this.level;
        this.points = Math.max(0, this.points + delta);
        this.level  = Level.of(this.points);
        return this.level != before;
    }

    // ── 試合中ボーナス計算 ────────────────────────────────
    /**
     * このペアが同フォーメーションで出場するときの能力ボーナスを返す。
     * key: ステータス名, value: ボーナス値
     */
    public java.util.Map<String, Integer> getMatchBonus() {
        java.util.Map<String, Integer> map = new java.util.LinkedHashMap<>();
        switch (level) {
            case LARGE -> {
                map.put("speed",     5); map.put("shooting", 5);
                map.put("passing",   5); map.put("defending",5);
                map.put("stamina",   5); map.put("spirit",   5);
            }
            case MEDIUM -> {
                map.put("speed",  4); map.put("stamina",  4);
                map.put("shooting",2); map.put("passing", 2);
            }
            case SMALL -> {
                map.put("speed", 2); map.put("stamina", 2);
            }
            default -> {}
        }
        return map;
    }

    // ── Getter ────────────────────────────────────────────
    public int   getPlayerIdA() { return playerIdA; }
    public int   getPlayerIdB() { return playerIdB; }
    public int   getPoints()    { return points; }
    public Level getLevel()     { return level; }

    /** 指定IDがこのペアに含まれるか */
    public boolean involves(int playerId) {
        return playerIdA == playerId || playerIdB == playerId;
    }

    /** 相手のIDを返す */
    public int getPartnerId(int myId) {
        return myId == playerIdA ? playerIdB : playerIdA;
    }

    /** DB保存用キー（"min_max"形式） */
    public String key() {
        return playerIdA + "_" + playerIdB;
    }

    /** static版キー生成（FriendshipManagerから使用） */
    public static String makeKey(int idA, int idB) {
        int a = Math.min(idA, idB);
        int b = Math.max(idA, idB);
        return a + "_" + b;
    }

    @Override
    public String toString() {
        return String.format("Friendship[%d↔%d  pt=%d  %s]",
            playerIdA, playerIdB, points, level.label);
    }
}
