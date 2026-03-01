package service;

import model.*;
import model.EmotionState.WeeklyEmotionResult;

import java.util.*;

/**
 * 感情システムの週次処理エンジン
 *
 * 毎週 WeeklyEventService.processWeek() の中から呼ぶ。
 * 各選手の EmotionState を更新し、発火すべきイベントのリストを返す。
 *
 * 使い方:
 *   EmotionEngine engine = new EmotionEngine();
 *   List<EmotionTrigger> triggers = engine.processWeek(club, matchResult);
 *   // triggers をもとに WeeklyEvent を生成してプレイヤーに提示する
 */
public class EmotionEngine {

    // ─────────────────────────────────────────────────────────
    // 週次処理（メインエントリ）
    // ─────────────────────────────────────────────────────────

    /**
     * クラブ全選手の感情を週次更新し、発火イベントを返す
     *
     * @param club         対象クラブ
     * @param playedIds    今週試合に出場した選手IDのセット
     * @param teamWon      チームが勝利したか
     * @return             発火すべきイベントのリスト（WeeklyEventServiceで処理）
     */
    public List<EmotionTrigger> processWeek(
            Club club, Set<Integer> playedIds, boolean teamWon) {

        List<EmotionTrigger> triggers = new ArrayList<>();

        for (Player p : club.getPlayers()) {
            if (p.getRole() == Player.PlayerRole.RETIRED) continue;

            BreedTrait trait = p.getBreedTrait();
            boolean played = playedIds.contains(p.getId());

            // 週次感情更新
            WeeklyEmotionResult result = p.getEmotion().processWeek(
                played, teamWon,
                trait.isShiba(),
                trait.isGolden(),
                trait.isFrenchBull()
            );

            // 自然減衰（rivalry など）
            p.getEmotion().naturalDecay();

            // spirit との同期（DB互換性のため spirit も更新する）
            syncSpiritFromEmotion(p);

            // イベントトリガー収集
            collectTriggers(p, result, triggers);
        }

        // チーム全体の team_harmony を更新
        updateTeamHarmony(club, playedIds);

        return triggers;
    }

    // ─────────────────────────────────────────────────────────
    // 試合中イベント（MatchSimulator から呼ぶ）
    // ─────────────────────────────────────────────────────────

    /** ゴール時の感情更新 */
    public void onGoal(Player scorer) {
        scorer.getEmotion().onGoal();
    }

    /** シュートミス時の感情更新 */
    public void onMiss(Player shooter) {
        BreedTrait trait = shooter.getBreedTrait();
        shooter.getEmotion().onMiss(trait.isShiba());
    }

    /** 接触ファウル時の感情更新 */
    public void onContactFoul(Player fouler) {
        BreedTrait trait = fouler.getBreedTrait();
        fouler.getEmotion().onContactFoul(trait.isBulldog());
    }

    /** 必殺技成功時の感情更新 */
    public void onSpecialMoveSuccess(Player player) {
        player.getEmotion().onSpecialMoveSuccess();
    }

    // ─────────────────────────────────────────────────────────
    // チーム全体の harmony 更新
    // ─────────────────────────────────────────────────────────

    /**
     * 出場選手の相性を計算して team_harmony を更新
     * ゴールデン在籍なら harmony の底を 30 以上に保つ
     */
    private void updateTeamHarmony(Club club, Set<Integer> playedIds) {
        List<Player> played = club.getPlayers().stream()
            .filter(p -> playedIds.contains(p.getId()))
            .toList();

        int harmonyDelta = 0;
        boolean hasGolden = false;

        for (int i = 0; i < played.size(); i++) {
            BreedTrait a = played.get(i).getBreedTrait();
            if (a.isGolden()) hasGolden = true;

            for (int j = i + 1; j < played.size(); j++) {
                BreedTrait b = played.get(j).getBreedTrait();
                int compat = a.compatibilityWith(b);
                harmonyDelta += compat * 8; // ◎=+8, ×=-8
            }
        }

        int current = club.getTeamHarmony();
        int next = Math.max(0, Math.min(100, current + harmonyDelta));

        // ゴールデン効果: 下限 30 を保護（チーム全体）
        if (hasGolden) next = Math.max(30, next);

        club.setTeamHarmony(next);
    }

    // ─────────────────────────────────────────────────────────
    // トリガー収集
    // ─────────────────────────────────────────────────────────

    private void collectTriggers(
            Player p, WeeklyEmotionResult result,
            List<EmotionTrigger> triggers) {

        if (result.frustrationEvent) {
            triggers.add(new EmotionTrigger(
                EmotionTrigger.Type.FRUSTRATION_WARNING, p,
                "【" + p.getFullName() + "】の不満が限界に近づいています。"));
        }
        if (result.rivalryEvent) {
            triggers.add(new EmotionTrigger(
                EmotionTrigger.Type.RIVALRY_HIGH, p,
                "【" + p.getFullName() + "】のライバル心が激化しています。"));
        }
        if (result.slumpTriggered) {
            triggers.add(new EmotionTrigger(
                EmotionTrigger.Type.SLUMP, p,
                "【" + p.getFullName() + "】がスランプに陥っています。\n面談や相談で回復を促しましょう。"));
        }
        if (result.benchWarning) {
            triggers.add(new EmotionTrigger(
                EmotionTrigger.Type.BENCH_WARNING, p,
                "【" + p.getFullName() + "】が3週以上出場していません。"));
        }
        if (result.trainingSkipRisk) {
            triggers.add(new EmotionTrigger(
                EmotionTrigger.Type.TRAINING_SKIP, p,
                "【" + p.getFullName() + "】が練習を無断欠席する可能性があります。"));
        }
        if (result.shibaSwing != null) {
            String dir = result.shibaSwing.equals("UP") ? "好調" : "不機嫌";
            triggers.add(new EmotionTrigger(
                EmotionTrigger.Type.SHIBA_MOOD_SWING, p,
                "【" + p.getFullName() + "】が突然" + dir + "になりました。（柴犬気質）"));
        }
        if (result.moodDown) {
            triggers.add(new EmotionTrigger(
                EmotionTrigger.Type.MOOD_DOWN, p,
                "【" + p.getFullName() + "】の調子が下がり気味です。"));
        }
    }

    // ─────────────────────────────────────────────────────────
    // spirit との同期（DB 互換性）
    // ─────────────────────────────────────────────────────────

    /**
     * EmotionState の状態を既存の spirit フィールドに反映
     * DB 保存・PlayerCardView の isSlump() 判定に使われるため維持する
     */
    private void syncSpiritFromEmotion(Player p) {
        EmotionState e = p.getEmotion();
        // mood + confidence の平均を spirit に反映
        int moodValue = switch (e.getMood()) {
            case HAPPY  -> 85;
            case NORMAL -> 70;
            case DOWN   -> 45;
            case SLUMP  -> 20;
        };
        int synced = (moodValue + e.getConfidence()) / 2;
        p.setSpirit(synced);
    }

    // ─────────────────────────────────────────────────────────
    // EmotionTrigger（イベント発火情報）
    // ─────────────────────────────────────────────────────────

    /**
     * 感情系イベントの発火情報を持つクラス
     * WeeklyEventService がこれを受け取ってイベントを生成する
     */
    public static class EmotionTrigger {

        public enum Type {
            FRUSTRATION_WARNING,  // frustration ≥ 70 → E001/E002
            RIVALRY_HIGH,         // rivalry ≥ 80 → ライバルイベント
            SLUMP,                // mood = SLUMP → E005
            BENCH_WARNING,        // 3週以上不出場 → frustration 警告
            TRAINING_SKIP,        // F.ブルドッグの練習スキップ
            SHIBA_MOOD_SWING,     // 柴犬のランダム気分変動
            MOOD_DOWN,            // mood が DOWN に悪化
        }

        public final Type   type;
        public final Player player;
        public final String message;

        public EmotionTrigger(Type type, Player player, String message) {
            this.type    = type;
            this.player  = player;
            this.message = message;
        }

        @Override public String toString() {
            return "[" + type + "] " + player.getFullName() + ": " + message;
        }
    }
}
