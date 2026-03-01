package model;

import java.util.List;

/**
 * 週次イベント エンティティ
 *
 * 毎週1回、WeeklyEventServiceが生成してClub/Playerに効果を適用する。
 * DBへはweekly_eventsテーブルに記録。
 */
public class WeeklyEvent {

    // ── イベント種別 ────────────────────────────────────────────
    public enum EventType {
        PLAYER_POSITIVE,   // 選手ポジティブ（能力UP）
        PLAYER_NEGATIVE,   // 選手ネガティブ（能力DOWN）
        CLUB_POSITIVE,     // クラブポジティブ（予算増・士気UP）
        CLUB_NEGATIVE,     // クラブネガティブ（予算減・士気DOWN）
        WEATHER,           // 天気・季節イベント（全選手影響）
        RIVAL_NEWS,        // ライバル情報（士気変動）
        NONE               // 平穏な週（効果なし）
    }

    // ── 効果種別 ────────────────────────────────────────────────
    public enum EffectType {
        STAT_UP,        // 特定能力値UP（speed/shooting/passing/defending/stamina/spirit）
        STAT_DOWN,      // 特定能力値DOWN
        ALL_STAT_UP,    // 全選手の特定能力値UP
        ALL_STAT_DOWN,  // 全選手の特定能力値DOWN
        BUDGET_CHANGE,  // クラブ予算変動
        NONE            // 効果なし（演出のみ）
    }

    // ── フィールド ──────────────────────────────────────────────
    private int       id;
    private int       week;
    private int       season;
    private EventType eventType;
    private String    eventKey;        // 識別子 例: TRAINING_INSPIRED
    private String    title;           // タイトル 例: ✨ 練習で開眼！
    private String    description;     // 本文（犬テーマ）
    private Player    targetPlayer;    // 対象選手（null=全体イベント）
    private EffectType effectType;
    private String    effectTarget;    // speed / shooting / passing / defending / stamina / spirit / budget
    private int       effectValue;     // 変動量（正=UP, 負=DOWN）

    // ── コンストラクタ ──────────────────────────────────────────
    public WeeklyEvent(int week, int season,
                       EventType eventType, String eventKey,
                       String title, String description,
                       Player targetPlayer,
                       EffectType effectType, String effectTarget, int effectValue) {
        this.week         = week;
        this.season       = season;
        this.eventType    = eventType;
        this.eventKey     = eventKey;
        this.title        = title;
        this.description  = description;
        this.targetPlayer = targetPlayer;
        this.effectType   = effectType;
        this.effectTarget = effectTarget;
        this.effectValue  = effectValue;
    }

    /**
     * 効果を選手に適用し、結果メッセージを返す。
     * WeeklyEventServiceから呼ぶ。
     */
    public String applyToPlayer(Player p) {
        if (p == null || effectType == EffectType.NONE
                      || effectType == EffectType.BUDGET_CHANGE) return "";

        int before = getStatValue(p, effectTarget);
        applyStatChange(p, effectTarget, effectValue);
        int after = getStatValue(p, effectTarget);

        String arrow = effectValue > 0 ? "▲" : "▼";
        String name  = targetPlayer != null ? targetPlayer.getFullName() : p.getFullName();
        return String.format("%s %s %s %d → %d (%s%d)",
            arrow, name, effectTarget, before, after,
            effectValue > 0 ? "+" : "", effectValue);
    }

    /**
     * 全選手への一括適用（WEATHER / RIVAL_NEWS / CLUB 系で使用）
     */
    public List<String> applyToAllPlayers(List<Player> squad) {
        return squad.stream()
                    .map(p -> applyToPlayer(p))
                    .filter(s -> !s.isBlank())
                    .toList();
    }

    // ── 内部ヘルパー ────────────────────────────────────────────
    private int getStatValue(Player p, String target) {
        return switch (target) {
            case "speed"     -> p.getSpeed();
            case "shooting"  -> p.getShooting();
            case "passing"   -> p.getPassing();
            case "defending" -> p.getDefending();
            case "stamina"   -> p.getStamina();
            case "spirit"    -> p.getSpirit();
            default          -> 0;
        };
    }

    private void applyStatChange(Player p, String target, int delta) {
        switch (target) {
            case "speed"     -> p.setSpeed    (clamp(p.getSpeed()     + delta));
            case "shooting"  -> p.setShooting (clamp(p.getShooting()  + delta));
            case "passing"   -> p.setPassing  (clamp(p.getPassing()   + delta));
            case "defending" -> p.setDefending(clamp(p.getDefending() + delta));
            case "stamina"   -> p.setStamina  (clamp(p.getStamina()   + delta));
            case "spirit"    -> p.setSpirit   (clamp(p.getSpirit()    + delta));
        }
    }

    private int clamp(int v) { return Math.max(1, Math.min(99, v)); }

    public String getSummary() {
        String who = targetPlayer != null ? targetPlayer.getFullName() : "クラブ全体";
        return String.format("[W%d] %s  %s  %s%s%+d",
            week, title, who, effectTarget,
            effectType == EffectType.NONE ? "(演出のみ)" : "→",
            effectValue);
    }

    // ── Getter / Setter ─────────────────────────────────────────
    public int        getId()           { return id; }
    public int        getWeek()         { return week; }
    public int        getSeason()       { return season; }
    public EventType  getEventType()    { return eventType; }
    public String     getEventKey()     { return eventKey; }
    public String     getTitle()        { return title; }
    public String     getDescription()  { return description; }
    public Player     getTargetPlayer() { return targetPlayer; }
    public EffectType getEffectType()   { return effectType; }
    public String     getEffectTarget() { return effectTarget; }
    public int        getEffectValue()  { return effectValue; }

    public void setId(int id)                     { this.id = id; }
    public void setTargetPlayer(Player p)         { this.targetPlayer = p; }
}
