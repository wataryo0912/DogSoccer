package model;

/**
 * 選手の感情状態を管理するクラス
 *
 * 設計書（game_design_AB.docx）の感情フロー仕様に基づく実装。
 * Player クラスに内包して使用する。
 *
 * ── パラメータ一覧 ──────────────────────────────────────────
 *   confidence   (0-100) 自信。ゴール/勝利で上昇、ミス/敗北で低下。
 *                        ≥90 でゾーン状態（試合中全能力+15%）
 *   frustration  (0-100) 不満。出場機会不足で蓄積。
 *                        ≥70 で「練習態度悪化」イベントへ
 *   loyalty      (0-100) 忠誠心。在籍年数・勝利で上昇。
 *                        移籍オファー受諾率に反比例
 *   rivalry      (0-100) ライバル心。接触プレーで蓄積。
 *                        ≥80 で「激しいライバル」イベントへ
 *   mood         (ENUM)  4段階の気分。週次で変動。
 *                        slump 中は全能力 -20%
 *   teamHarmony  は Club 側で管理（選手個人ではなくチーム全体の値）
 */
public class EmotionState {

    // ── Mood 定義 ─────────────────────────────────────────────
    public enum Mood {
        HAPPY  ("好調",  +0.10),   // 全能力 +10%
        NORMAL ("普通",  +0.00),   // 補正なし
        DOWN   ("不調",  -0.10),   // 全能力 -10%
        SLUMP  ("スランプ", -0.20); // 全能力 -20%、特別イベントで回復

        public final String label;
        public final double statMultiplier; // 能力値への乗数ボーナス

        Mood(String label, double mult) {
            this.label = label;
            this.statMultiplier = mult;
        }
    }

    // ── パラメータ（すべて 0〜100） ──────────────────────────
    private int  confidence  = 60;
    private int  frustration = 0;
    private int  loyalty     = 50;
    private int  rivalry     = 0;
    private Mood mood        = Mood.NORMAL;

    // ── 状態フラグ ────────────────────────────────────────────
    /** ゾーン状態（confidence≥90 達成時、1試合限定） */
    private boolean inZone        = false;
    /** スランプ連続週数（2週以上で SLUMP へ移行） */
    private int     downStreak    = 0;
    /** ベンチ/非出場が続いた週数（frustration 蓄積に使用） */
    private int     benchedWeeks  = 0;

    // ════════════════════════════════════════════════════════════
    // 試合イベントによる変動（MatchSimulator から呼ぶ）
    // ════════════════════════════════════════════════════════════

    /** ゴール決定時 */
    public void onGoal() {
        addConfidence(+12);
        if (confidence >= 90) inZone = true;
    }

    /** シュートミス時 */
    public void onMiss(boolean isShiba) {
        addConfidence(isShiba ? -15 : -8);
    }

    /** 接触系ファウル時（タックル受けた/した） */
    public void onContactFoul(boolean isBulldog) {
        addRivalry(isBulldog ? +20 : +10);
    }

    /** 必殺技成功時 */
    public void onSpecialMoveSuccess() {
        addConfidence(+20);
        mood = Mood.HAPPY; // 必殺技成功は即 happy 確定
        inZone = true;
    }

    // ════════════════════════════════════════════════════════════
    // 週次処理（WeeklyEventService から呼ぶ）
    // ════════════════════════════════════════════════════════════

    /**
     * 週次感情更新
     * @param played        今週試合に出場したか
     * @param teamWon       チームが勝利したか
     * @param isShiba       柴犬かどうか（ランダム気分変動）
     * @param isGolden      ゴールデンレトリバーかどうか（mood下限保護）
     * @param isFrenchBull  フレンチブルドッグ（練習スキップリスク）
     */
    public WeeklyEmotionResult processWeek(
            boolean played, boolean teamWon,
            boolean isShiba, boolean isGolden, boolean isFrenchBull) {

        WeeklyEmotionResult result = new WeeklyEmotionResult();

        // ── 出場/ベンチの判定 ──────────────────────────────────
        if (played) {
            benchedWeeks = 0;
            if (teamWon) {
                addLoyalty(+3);
                addConfidence(+5);
            } else {
                // 敗北 → mood 悪化候補
                addFrustration(+5);
                moodDownCandidate(result);
            }
        } else {
            // 出場なし → frustration 蓄積
            benchedWeeks++;
            if (benchedWeeks >= 3) {
                addFrustration(+20);
                result.benchWarning = true;
            }
        }

        // ── 柴犬ランダム気分変動（30%確率） ───────────────────
        if (isShiba && Math.random() < 0.30) {
            if (Math.random() < 0.5) {
                mood = moodUp(mood);
                result.shibaSwing = "UP";
            } else {
                mood = moodDown(mood, isGolden);
                result.shibaSwing = "DOWN";
            }
        }

        // ── フレンチブルドッグ 練習スキップリスク（15%） ──────
        if (isFrenchBull && Math.random() < 0.15) {
            result.trainingSkipRisk = true;
        }

        // ── ゾーン状態リセット（1試合限定なので週次でリセット） -
        inZone = false;

        // ── slump 判定 ─────────────────────────────────────────
        if (mood == Mood.DOWN) {
            downStreak++;
            if (downStreak >= 2) {
                mood = Mood.SLUMP;
                result.slumpTriggered = true;
                downStreak = 0;
            }
        } else {
            downStreak = 0;
        }

        // ── ゴールデン: team_harmony の底上げ（Clubクラス側で処理） ──
        // ゴールデン本人の mood は NORMAL より下には下がらない保護
        if (isGolden && mood == Mood.DOWN) {
            mood = Mood.NORMAL;
        }

        // ── frustration 閾値チェック ──────────────────────────
        if (frustration >= 70) {
            result.frustrationEvent = true;
        }
        // rivalry 閾値チェック
        if (rivalry >= 80) {
            result.rivalryEvent = true;
        }

        return result;
    }

    /** 試合後の自然回復（rivalry はゆっくり減衰） */
    public void naturalDecay() {
        // rivalry は週次で -5 ずつ自然減衰
        addRivalry(-5);
        // confidence も極端に高い場合は少し戻る
        if (confidence > 85) addConfidence(-2);
    }

    // ════════════════════════════════════════════════════════════
    // イベント解決（WeeklyEventService のイベント選択肢から呼ぶ）
    // ════════════════════════════════════════════════════════════

    public void resolveConsultation(int hotlineDelta) {
        // E006: チームメイト相談 → slump 回復可能性
        if (mood == Mood.SLUMP) {
            double recoverChance = 0.25 + (hotlineDelta / 100.0) * 0.30;
            if (Math.random() < recoverChance) {
                mood = Mood.NORMAL;
            }
        }
        addLoyalty(+3);
    }

    public void resolveFrustrationTalk() {
        // E001: 個別面談
        addFrustration(-20);
        addLoyalty(+5);
    }

    public void resolveFrustrationWatch() {
        // E001: 様子を見る
        addFrustration(-5);
    }

    public void resolveFrustrationPublic() {
        // E001: 公開注意（team_harmony -8 は Club 側で処理）
        addFrustration(-10);
    }

    public void onSpecialMoveAcquired() {
        // E021: 必殺技習得
        addConfidence(+15);
    }

    public void onZoneActivated() {
        // E022: ゾーン状態イベント（試合後 confidence +10）
        addConfidence(+5);
        inZone = true;
    }

    // ════════════════════════════════════════════════════════════
    // 能力値への影響（MatchSimulator が参照する）
    // ════════════════════════════════════════════════════════════

    /**
     * 現在の感情状態から能力補正倍率を返す
     * MatchSimulator で各能力値にこの値を掛ける
     */
    public double getStatMultiplier() {
        double base = mood.statMultiplier;

        // ゾーン状態は mood 補正を上書きして +15%
        if (inZone) return +0.15;

        // confidence が極端に低い場合は追加ペナルティ
        if (confidence < 30) base -= 0.05;

        return base;
    }

    /**
     * シュート精度への追加補正（confidence ベース）
     * 0.0 〜 +0.10 の範囲で返す
     */
    public double getShootingBonus() {
        // confidence 60 を基準に ±0.10
        return (confidence - 60) / 600.0;
    }

    // ════════════════════════════════════════════════════════════
    // 内部ユーティリティ
    // ════════════════════════════════════════════════════════════

    private void addConfidence(int delta)  { confidence  = clamp(confidence  + delta); }
    private void addFrustration(int delta) { frustration = clamp(frustration + delta); }
    private void addLoyalty(int delta)     { loyalty     = clamp(loyalty     + delta); }
    private void addRivalry(int delta)     { rivalry     = clamp(rivalry     + delta); }
    private int  clamp(int v)             { return Math.max(0, Math.min(100, v)); }

    private void moodDownCandidate(WeeklyEmotionResult result) {
        // 連続敗北などで mood が下がる候補になる
        if (mood == Mood.HAPPY)  { mood = Mood.NORMAL; }
        else if (mood == Mood.NORMAL) { mood = Mood.DOWN; result.moodDown = true; }
        // DOWN/SLUMP はそのまま
    }

    private Mood moodUp(Mood current) {
        return switch (current) {
            case SLUMP  -> Mood.DOWN;
            case DOWN   -> Mood.NORMAL;
            case NORMAL -> Mood.HAPPY;
            case HAPPY  -> Mood.HAPPY;
        };
    }

    private Mood moodDown(Mood current, boolean isGolden) {
        Mood next = switch (current) {
            case HAPPY  -> Mood.NORMAL;
            case NORMAL -> Mood.DOWN;
            case DOWN   -> Mood.SLUMP;
            case SLUMP  -> Mood.SLUMP;
        };
        // ゴールデンは DOWN より下には下がらない
        if (isGolden && next == Mood.SLUMP) return Mood.DOWN;
        return next;
    }

    // ════════════════════════════════════════════════════════════
    // Getters / Setters（DB保存・DAO用）
    // ════════════════════════════════════════════════════════════

    public int  getConfidence()  { return confidence; }
    public int  getFrustration() { return frustration; }
    public int  getLoyalty()     { return loyalty; }
    public int  getRivalry()     { return rivalry; }
    public Mood getMood()        { return mood; }
    public boolean isInZone()    { return inZone; }
    public int  getBenchedWeeks(){ return benchedWeeks; }

    public void setConfidence(int v)   { this.confidence  = clamp(v); }
    public void setFrustration(int v)  { this.frustration = clamp(v); }
    public void setLoyalty(int v)      { this.loyalty     = clamp(v); }
    public void setRivalry(int v)      { this.rivalry     = clamp(v); }
    public void setMood(Mood v)        { this.mood        = v != null ? v : Mood.NORMAL; }
    public void setBenchedWeeks(int v) { this.benchedWeeks = Math.max(0, v); }

    @Override
    public String toString() {
        return String.format("EmotionState[mood=%s conf=%d frust=%d loyal=%d rival=%d%s]",
            mood.label, confidence, frustration, loyalty, rivalry,
            inZone ? " ⚡ZONE" : "");
    }

    // ════════════════════════════════════════════════════════════
    // 週次処理の結果を返す内部クラス
    // ════════════════════════════════════════════════════════════

    /**
     * processWeek() の戻り値。
     * WeeklyEventService がこれを見てイベントを発火させる。
     */
    public static class WeeklyEmotionResult {
        public boolean frustrationEvent  = false; // frustration ≥ 70 → E001/E002
        public boolean rivalryEvent      = false; // rivalry ≥ 80 → 激しいライバル
        public boolean benchWarning      = false; // 3週以上ベンチ
        public boolean moodDown          = false; // mood が DOWN に悪化
        public boolean slumpTriggered    = false; // mood が SLUMP に移行
        public boolean trainingSkipRisk  = false; // F.ブルドッグの練習スキップリスク
        public String  shibaSwing        = null;  // "UP" or "DOWN"（柴犬のみ）
    }
}
