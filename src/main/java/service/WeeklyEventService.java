package service;

import db.WeeklyEventDao;
import db.EmotionStateDao;
import model.*;
import model.WeeklyEvent.*;
import model.EmotionState.Mood;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

/**
 * 週次ランダムイベント生成・適用サービス（感情システム統合版）
 *
 * 変更点（既存からの差分）:
 *   1. EmotionEngine を内包
 *   2. generateAndApply() の末尾で感情週次処理を実行
 *   3. calcWeight() が感情パラメータを参照
 *   4. selectTarget() が frustration/slump を考慮
 *   5. 感情トリガーをイベント定義に追加（E_FRUSTRATION等）
 *   6. EmotionStateDao で DB 保存
 */
public class WeeklyEventService {

    private final Random          rng        = new Random();
    private final WeeklyEventDao  dao        = new WeeklyEventDao();
    private final EmotionEngine   emotionEngine = new EmotionEngine();

    // DB接続（外から注入、なければnullセーフ動作）
    private Connection dbConn = null;

    public void setConnection(Connection conn) { this.dbConn = conn; }

    // ════════════════════════════════════════════════════════════
    // イベント定義
    // ════════════════════════════════════════════════════════════

    private record EventDef(
        String     key,
        EventType  eventType,
        String     titleTemplate,
        String     descTemplate,
        EffectType effectType,
        String     effectTarget,
        int        baseValue,
        int        jitter,
        String     seasons
    ) {}

    private static final List<EventDef> DEFS = List.of(

        // ── 既存イベント（変更なし） ──────────────────────────
        new EventDef("TRAINING_INSPIRED", EventType.PLAYER_POSITIVE,
            "✨ {player} が練習で開眼！",
            "{player}が練習中に突然コツをつかみ、目をキラキラさせながら走り回った！スピードと全身能力が向上！",
            EffectType.STAT_UP, "speed", 4, 2, "ALL"),

        new EventDef("SHOOTING_PRACTICE", EventType.PLAYER_POSITIVE,
            "🎯 {player} のシュート特訓大成功！",
            "居残り特訓で何百本もシュートを蹴り続けた{player}。今やゴール前では怖いものなし！",
            EffectType.STAT_UP, "shooting", 5, 2, "ALL"),

        new EventDef("PASSING_VISION", EventType.PLAYER_POSITIVE,
            "👁 {player} のパスセンス覚醒！",
            "{player}が仲間の動きをまるで予知するかのような神業パスを連発し始めた！",
            EffectType.STAT_UP, "passing", 5, 2, "SPRING,AUTUMN"),

        new EventDef("DEFENSE_WALL", EventType.PLAYER_POSITIVE,
            "🛡 {player} が鉄壁の守備を習得！",
            "{player}が最終ラインで吠え続けた結果、相手FWが恐れて近づかなくなった！",
            EffectType.STAT_UP, "defending", 5, 2, "AUTUMN,WINTER"),

        new EventDef("PUPPY_ENERGY", EventType.PLAYER_POSITIVE,
            "🐶 {player} の仔犬パワーが炸裂！",
            "春の陽気で{player}のスタミナが大爆発！試合後もハイテンションで走り続けている！",
            EffectType.STAT_UP, "stamina", 6, 2, "SPRING"),

        new EventDef("SPIRIT_UP", EventType.PLAYER_POSITIVE,
            "🔥 {player} に根性の火が付いた！",
            "サポーターからの熱い応援メッセージを読んだ{player}。尻尾をぶんぶん振りながら練習に励む！",
            EffectType.STAT_UP, "spirit", 6, 2, "ALL"),

        new EventDef("VETERAN_WISDOM", EventType.PLAYER_POSITIVE,
            "🎓 {player} の老犬の知恵が光る！",
            "ベテランの{player}が若手に自分の経験を丁寧に伝授。その過程で自身の能力も再確認！",
            EffectType.STAT_UP, "passing", 4, 1, "ALL"),

        new EventDef("GOOD_MEAL", EventType.PLAYER_POSITIVE,
            "🍖 {player} が特製ごはんで体力回復！",
            "栄養士が考案した特製ドッグフードを食べた{player}のスタミナが見る見る回復していった！",
            EffectType.STAT_UP, "stamina", 5, 2, "ALL"),

        new EventDef("INJURY_MINOR", EventType.PLAYER_NEGATIVE,
            "🤕 {player} が軽傷を負ってしまった…",
            "{player}が練習中に足を少し引っかけてしまった。暫くスタミナが落ちそうだ…",
            EffectType.STAT_DOWN, "stamina", -7, 2, "ALL"),

        new EventDef("OVEREATING", EventType.PLAYER_NEGATIVE,
            "🍖 {player} が食べすぎ注意報！",
            "{player}が冬のご褒美おやつを食べすぎてしまった。走るのが少し辛そう…",
            EffectType.STAT_DOWN, "speed", -4, 1, "WINTER"),

        new EventDef("SLUMP", EventType.PLAYER_NEGATIVE,
            "😞 {player} がスランプに突入…",
            "{player}のシュートが急に枠を外れ始めた。耳が垂れ下がりっぱなしで元気がない…",
            EffectType.STAT_DOWN, "shooting", -5, 2, "ALL"),

        new EventDef("DISTRACTED", EventType.PLAYER_NEGATIVE,
            "🦋 {player} がちょうちょに夢中！",
            "{player}が練習中に飛んできたちょうちょを追いかけて集中力が散漫に！精神力が落ちた…",
            EffectType.STAT_DOWN, "spirit", -5, 2, "SPRING,SUMMER"),

        new EventDef("COLD", EventType.PLAYER_NEGATIVE,
            "🤧 {player} が体調を崩してしまった…",
            "{player}が夏バテ（または冬の風邪）で体調不良。早い回復を祈ろう…",
            EffectType.STAT_DOWN, "stamina", -6, 2, "SUMMER,WINTER"),

        new EventDef("HOMESICK", EventType.PLAYER_NEGATIVE,
            "🏠 {player} がホームシック気味…",
            "{player}が故郷の家族を想って夜泣きをしているらしい。精神面に影響が出ている…",
            EffectType.STAT_DOWN, "spirit", -4, 1, "ALL"),

        new EventDef("DISTRACTION_SQUIRREL", EventType.PLAYER_NEGATIVE,
            "🐿 {player} がリスを発見して暴走！",
            "練習中にリスを見つけた{player}がグラウンドを飛び出してしまった！守備が疎かに…",
            EffectType.STAT_DOWN, "defending", -4, 2, "SUMMER,AUTUMN"),

        new EventDef("SPONSOR_BONUS", EventType.CLUB_POSITIVE,
            "💰 スポンサーから追加ボーナス！",
            "地元企業が犬テーマのサッカーに感動してスポンサー契約を追加！クラブに臨時収入！",
            EffectType.BUDGET_CHANGE, "budget", 3_000_000, 1_000_000, "ALL"),

        new EventDef("MEDIA_BUZZ", EventType.CLUB_POSITIVE,
            "📺 メディアが大注目！グッズ爆売れ！",
            "テレビ局が犬サッカーの特集を組んでくれた！グッズ売上が急増し収益がUP！",
            EffectType.BUDGET_CHANGE, "budget", 1_500_000, 500_000, "ALL"),

        new EventDef("COMMUNITY_EVENT", EventType.CLUB_POSITIVE,
            "🎪 地域犬サッカー体験教室が大成功！",
            "子どもたちが大喜びした犬サッカー体験教室。笑顔と共にクラブに収益が入ってきた！",
            EffectType.BUDGET_CHANGE, "budget", 1_000_000, 300_000, "SPRING,SUMMER"),

        new EventDef("TEAM_MORALE_UP", EventType.CLUB_POSITIVE,
            "🎉 BBQパーティーでチームが結束！",
            "クラブ主催のBBQパーティーで選手全員が大はしゃぎ！チーム全体の精神力が向上！",
            EffectType.ALL_STAT_UP, "spirit", 3, 1, "ALL"),

        new EventDef("EQUIPMENT_DAMAGE", EventType.CLUB_NEGATIVE,
            "🔧 設備の修繕費が発生…",
            "ある選手が練習場の設備を壊してしまった（名前は伏せます）！修繕費が必要に…",
            EffectType.BUDGET_CHANGE, "budget", -500_000, 100_000, "ALL"),

        new EventDef("FINE", EventType.CLUB_NEGATIVE,
            "📋 リーグから反則金が…",
            "先週の試合での過激なプレーにリーグから罰金が科された！クラブ運営に打撃…",
            EffectType.BUDGET_CHANGE, "budget", -300_000, 100_000, "ALL"),

        new EventDef("TEAM_MORALE_DOWN", EventType.CLUB_NEGATIVE,
            "😔 チームの士気が低下…",
            "連敗続きの影響でロッカールームの雰囲気が重い…。全選手の精神力に影響が出ている。",
            EffectType.ALL_STAT_DOWN, "spirit", -3, 1, "ALL"),

        new EventDef("HEATWAVE", EventType.WEATHER,
            "☀️ 猛暑日！犬には辛い季節…",
            "記録的な猛暑で練習を短縮！犬は暑さに弱い！全選手のスタミナに影響が出た…",
            EffectType.ALL_STAT_DOWN, "stamina", -3, 1, "SUMMER"),

        new EventDef("PERFECT_WEATHER", EventType.WEATHER,
            "🌸 絶好のコンディション！",
            "さわやかな気候で練習が絶好調！全選手が気持ちよく走り回っている！",
            EffectType.ALL_STAT_UP, "stamina", 3, 1, "SPRING,AUTUMN"),

        new EventDef("RAINY_SEASON", EventType.WEATHER,
            "🌧 長雨でグラウンド不良…",
            "長雨でグラウンドがぬかるみ、練習の質が落ちてしまった…全体的に影響が出ている。",
            EffectType.ALL_STAT_DOWN, "stamina", -2, 1, "SUMMER"),

        new EventDef("FIRST_SNOW", EventType.WEATHER,
            "❄️ 初雪に選手たちが大興奮！",
            "初雪に大興奮した選手たちが雪の中を駆け回った！その結果、精神力が大幅UP！",
            EffectType.ALL_STAT_UP, "spirit", 4, 1, "WINTER"),

        new EventDef("CHERRY_BLOSSOM", EventType.WEATHER,
            "🌸 桜の下でお花見練習！",
            "満開の桜の下でお花見練習会を開催！選手たちのテンションが最高潮に！",
            EffectType.ALL_STAT_UP, "spirit", 3, 1, "SPRING"),

        new EventDef("RIVAL_INJURY", EventType.RIVAL_NEWS,
            "🏥 ライバルの主力が負傷との情報！",
            "ライバルクラブのエース格が負傷しているとの情報が入ってきた！チャンスが広がった！",
            EffectType.ALL_STAT_UP, "spirit", 2, 1, "ALL"),

        new EventDef("RIVAL_TRANSFER", EventType.RIVAL_NEWS,
            "⚡ ライバルが大物選手を獲得！",
            "強豪クラブが噂の大物選手を獲得したという情報が流れ…選手たちが少し不安そうだ。",
            EffectType.ALL_STAT_DOWN, "spirit", -2, 1, "ALL"),

        new EventDef("DREAM_BONE", EventType.PLAYER_POSITIVE,
            "🦴 {player} が夢の中で骨を発見！",
            "{player}が昼寝中に足をバタバタさせながら夢を見ていた！夢の中で練習した動きが体に染み込んだ！",
            EffectType.STAT_UP, "speed", 3, 1, "ALL"),

        new EventDef("FAN_LETTER", EventType.PLAYER_POSITIVE,
            "💌 {player} にファンレターが届いた！",
            "子どものサポーターから手書きのファンレターが届いた！感動して涙ぐんだ{player}の自信がみなぎった！",
            EffectType.STAT_UP, "spirit", 5, 2, "ALL"),

        new EventDef("AGILITY_TRAINING", EventType.PLAYER_POSITIVE,
            "🐾 {player} がアジリティ訓練で大躍進！",
            "障害物コースでベストタイムを叩き出した！{player}の俊敏性が大幅アップ！",
            EffectType.STAT_UP, "speed", 6, 2, "SPRING,SUMMER"),

        new EventDef("NEW_TOY", EventType.PLAYER_POSITIVE,
            "🧸 {player} に新しいおもちゃ！",
            "マネージャーがプレゼントした新しいおもちゃに{player}が大興奮！遊びの中で俊敏性が向上！",
            EffectType.STAT_UP, "speed", 4, 2, "ALL"),

        new EventDef("RIVAL_MOCKERY", EventType.PLAYER_NEGATIVE,
            "😤 {player} がライバルにバカにされた！",
            "ライバル選手に「犬ごときが」と言われてしまった{player}…傷ついてはいるが、怒りのエネルギーにもなりそう。",
            EffectType.STAT_DOWN, "spirit", -3, 1, "ALL"),

        new EventDef("THUNDER_SCARED", EventType.PLAYER_NEGATIVE,
            "⚡ {player} が雷に驚いて隠れた！",
            "嵐の夜に雷が鳴り響き、{player}は毛布の中から出てこなかった…スタミナが消耗してしまった。",
            EffectType.STAT_DOWN, "stamina", -4, 2, "SUMMER,WINTER"),

        new EventDef("BATH_REFUSAL", EventType.PLAYER_NEGATIVE,
            "🛁 {player} がお風呂を拒否！",
            "お風呂の時間になると{player}が逃げ回ってしまい、コンディション維持に支障が…",
            EffectType.STAT_DOWN, "stamina", -3, 1, "ALL"),

        new EventDef("FULL_MOON_HOWL", EventType.CLUB_POSITIVE,
            "🌕 満月の夜にチーム全員で遠吠え！",
            "満月の夜、グラウンドに集まって全員で遠吠え！奇妙な儀式でチームの絆が深まった！",
            EffectType.ALL_STAT_UP, "spirit", 4, 1, "ALL"),

        new EventDef("DOGPARK_OUTING", EventType.CLUB_POSITIVE,
            "🌳 チームでドッグランに遠足！",
            "練習の合間にドッグランへ！自由に走り回ったことでチーム全体のスタミナが回復！",
            EffectType.ALL_STAT_UP, "stamina", 3, 1, "SPRING,SUMMER,AUTUMN"),

        new EventDef("NAUGHTY_CHEWING", EventType.CLUB_NEGATIVE,
            "😫 選手がユニフォームを噛んでしまった！",
            "誰かがゴール裏に置いてあったユニフォームをかじってしまった（名前は伏せます）！修繕費発生…",
            EffectType.BUDGET_CHANGE, "budget", -200_000, 50_000, "ALL"),

        // ── 感情システム連動イベント（NEW） ──────────────────
        // E001: 練習態度悪化（frustration ≥ 70 で発火）
        new EventDef("E_FRUSTRATION_WARNING", EventType.PLAYER_NEGATIVE,
            "😠 {player} の練習態度が悪化…",
            "{player}が練習を途中でサボり始めた。不満が限界に近づいているようだ。早めに話し合いが必要かもしれない。",
            EffectType.STAT_DOWN, "spirit", -5, 2, "ALL"),

        // E002: 移籍要求（frustration ≥ 70 が解消されなかった場合）
        new EventDef("E_TRANSFER_REQUEST", EventType.PLAYER_NEGATIVE,
            "🚪 {player} が移籍を要求！",
            "{player}が監督室を訪ね、移籍を希望していることを伝えてきた。不満が限界を超えてしまったようだ…",
            EffectType.STAT_DOWN, "spirit", -10, 2, "ALL"),

        // E005: スランプ深刻化（mood == SLUMP で発火）
        new EventDef("E_SLUMP_SERIOUS", EventType.PLAYER_NEGATIVE,
            "😰 {player} のスランプが深刻に…",
            "{player}の不調が長引いている。ボールを蹴るたびに首を傾け、自信を失っているようだ。相談の場を設けてみては？",
            EffectType.STAT_DOWN, "shooting", -8, 2, "ALL"),

        // E006: チームメイト相談（手動起動、slump回復）
        new EventDef("E_CONSULTATION", EventType.PLAYER_POSITIVE,
            "💬 {player} がチームメイトに相談",
            "{player}が信頼できる仲間に悩みを打ち明けた。話を聞いてもらったことで、少し気持ちが楽になったようだ。",
            EffectType.STAT_UP, "spirit", 8, 3, "ALL"),

        // E_ZONE: ゾーン状態（confidence ≥ 90）
        new EventDef("E_ZONE", EventType.PLAYER_POSITIVE,
            "⚡ {player} がゾーンに突入！",
            "{player}の動きが別次元に入った！全ての判断が研ぎ澄まされ、今週の試合では全能力+15%で臨める！",
            EffectType.STAT_UP, "spirit", 10, 0, "ALL"),

        // E_SHIBA_HAPPY: 柴犬のランダム好調
        new EventDef("E_SHIBA_HAPPY", EventType.PLAYER_POSITIVE,
            "🐕 {player} が突然ご機嫌に！",
            "理由は分からないが、{player}が今日は特別嬉しそうだ。尻尾の振り方が尋常じゃない！",
            EffectType.STAT_UP, "spirit", 6, 2, "ALL"),

        // E_SHIBA_GRUMPY: 柴犬のランダム不機嫌
        new EventDef("E_SHIBA_GRUMPY", EventType.PLAYER_NEGATIVE,
            "😒 {player} が突然不機嫌に…",
            "昨日まで元気だった{player}が今日は拗ねている。柴犬にはよくあることらしいが、原因は謎だ。",
            EffectType.STAT_DOWN, "spirit", -4, 2, "ALL"),

        // ── 平穏な週 ──────────────────────────────────────────
        new EventDef("PEACEFUL_WEEK", EventType.NONE,
            "☀️ 今週は穏やかな一週間でした",
            "特に大きな出来事もなく、選手たちは黙々と練習に励んでいる。これが一番だ！",
            EffectType.NONE, "", 0, 0, "ALL")
    );

    // ════════════════════════════════════════════════════════════
    // メインメソッド
    // ════════════════════════════════════════════════════════════

    /**
     * イベントを生成・適用・DB保存して返す。
     * WeeklyPlanView から毎週呼ぶ。既存インターフェースは変更なし。
     *
     * @param club    対象クラブ
     * @param week    現在の週番号
     * @param season  現在のシーズン番号
     * @param playedIds  今週出場した選手IDのセット（感情システム用、なければ空Set）
     * @param teamWon    チームが勝利したか
     */
    public WeeklyEvent generateAndApply(
            Club club, int week, int season,
            Set<Integer> playedIds, boolean teamWon) {

        SeasonManager.Season currentSeason = toSeason(week);

        // ── ① 感情週次処理（NEW） ─────────────────────────────
        List<EmotionEngine.EmotionTrigger> triggers =
            emotionEngine.processWeek(club, playedIds, teamWon);

        // ── ② トリガーから優先イベントを決定 ──────────────────
        WeeklyEvent event = resolveFromTriggers(triggers, club, week, season)
            .orElseGet(() -> generate(club, week, season, currentSeason));

        // ── ③ 効果を適用 ──────────────────────────────────────
        applyEffect(event, club);

        // ── ④ 感情パラメータへの反映 ─────────────────────────
        applyEmotionEffect(event);

        // ── ⑤ 仲良し度処理（既存コードそのまま） ──────────────
        List<String> friendMsgs = ui.MainApp.friendshipManager.recordEvent(
            event.getTargetPlayer(), club.getSquad());
        if (!friendMsgs.isEmpty()) {
            System.out.println("[Friendship] " + String.join(" / ", friendMsgs));
        }

        // ── ⑥ DB保存 ──────────────────────────────────────────
        try {
            dao.insert(event);
        } catch (SQLException e) {
            System.err.println("[WeeklyEvent] DB保存エラー: " + e.getMessage());
        }

        // 感情パラメータをDBに保存
        saveEmotions(club.getPlayers());

        return event;
    }

    /**
     * 既存コードとの後方互換メソッド（playedIds・teamWon なし版）
     * 感情週次処理はスキップされる
     */
    public WeeklyEvent generateAndApply(Club club, int week, int season) {
        return generateAndApply(club, week, season, Collections.emptySet(), false);
    }

    // ════════════════════════════════════════════════════════════
    // 感情トリガーからイベント解決
    // ════════════════════════════════════════════════════════════

    /**
     * EmotionTrigger から最優先のイベントを1つ選んで返す。
     * トリガーがなければ empty を返し、通常ランダム抽選に委ねる。
     */
    private Optional<WeeklyEvent> resolveFromTriggers(
            List<EmotionEngine.EmotionTrigger> triggers,
            Club club, int week, int season) {

        if (triggers.isEmpty()) return Optional.empty();

        // 優先度順: FRUSTRATION → SLUMP → RIVALRY → その他
        var sortedTriggers = triggers.stream()
            .sorted(Comparator.comparingInt(t -> triggerPriority(t.type)))
            .toList();

        var top = sortedTriggers.get(0);

        String defKey = switch (top.type) {
            case FRUSTRATION_WARNING -> "E_FRUSTRATION_WARNING";
            case SLUMP               -> "E_SLUMP_SERIOUS";
            case SHIBA_MOOD_SWING    -> (top.message.contains("UP") || top.message.contains("好調"))
                                        ? "E_SHIBA_HAPPY" : "E_SHIBA_GRUMPY";
            case RIVALRY_HIGH        -> "RIVAL_MOCKERY"; // 既存イベント流用
            case BENCH_WARNING       -> "E_FRUSTRATION_WARNING";
            case TRAINING_SKIP       -> "BATH_REFUSAL"; // 既存イベント流用
            default                  -> null;
        };

        if (defKey == null) return Optional.empty();

        final String key = defKey;
        return DEFS.stream()
            .filter(d -> d.key().equals(key))
            .findFirst()
            .map(def -> buildEvent(def, top.player, week, season));
    }

    private int triggerPriority(EmotionEngine.EmotionTrigger.Type type) {
        return switch (type) {
            case FRUSTRATION_WARNING -> 0;
            case SLUMP               -> 1;
            case RIVALRY_HIGH        -> 2;
            case SHIBA_MOOD_SWING    -> 3;
            case BENCH_WARNING       -> 4;
            default                  -> 5;
        };
    }

    // ════════════════════════════════════════════════════════════
    // 感情パラメータへの反映
    // ════════════════════════════════════════════════════════════

    /**
     * イベント発生後に感情パラメータを調整する
     * （spirit の変動を EmotionState にも反映する）
     */
    private void applyEmotionEffect(WeeklyEvent event) {
        Player target = event.getTargetPlayer();
        if (target == null) return;

        EmotionState e = target.getEmotion();
        int spiritDelta = event.getEffectValue();

        switch (event.getEventKey()) {
            case "E_FRUSTRATION_WARNING" -> e.resolveFrustrationWatch();
            case "E_SLUMP_SERIOUS"       -> { /* スランプは自然経過 */ }
            case "E_CONSULTATION"        -> {
                // 相談相手との仲良し度から回復率を算出
                int hotline = 50; // TODO: FriendshipManagerから取得
                e.resolveConsultation(hotline);
            }
            case "E_ZONE"                -> e.onZoneActivated();
            case "E_SHIBA_HAPPY"         -> { /* processWeek内で処理済み */ }
            case "E_SHIBA_GRUMPY"        -> { /* processWeek内で処理済み */ }
            case "SPIRIT_UP", "FAN_LETTER" -> {
                // ポジティブイベント → confidence も上昇
                if (spiritDelta > 0) e.setConfidence(
                    Math.min(100, e.getConfidence() + spiritDelta / 2));
            }
            case "SLUMP", "HOMESICK", "RIVAL_MOCKERY" -> {
                // ネガティブイベント → confidence も低下
                if (spiritDelta < 0) e.setConfidence(
                    Math.max(0, e.getConfidence() + spiritDelta / 2));
            }
        }
    }

    // ════════════════════════════════════════════════════════════
    // 生成ロジック（既存コードベース・感情パラメータ考慮を追加）
    // ════════════════════════════════════════════════════════════

    private WeeklyEvent generate(Club club, int week, int season,
                                  SeasonManager.Season currentSeason) {
        List<EventDef> candidates = new ArrayList<>();
        for (EventDef d : DEFS) {
            // 感情トリガー専用イベントは通常抽選に含めない
            if (isEmotionOnlyEvent(d.key())) continue;
            if (matchesSeason(d.seasons(), currentSeason)) {
                int weight = calcWeight(d, club, currentSeason);
                for (int i = 0; i < weight; i++) candidates.add(d);
            }
        }
        if (candidates.isEmpty()) candidates.addAll(DEFS);

        EventDef def = candidates.get(rng.nextInt(candidates.size()));
        Player target = selectTarget(def.eventType(), club.getSquad());
        return buildEvent(def, target, week, season);
    }

    private boolean isEmotionOnlyEvent(String key) {
        return key.startsWith("E_");
    }

    private WeeklyEvent buildEvent(EventDef def, Player target, int week, int season) {
        String playerName = target != null ? target.getFullName() : "チーム";
        String title = def.titleTemplate().replace("{player}", playerName);
        String desc  = def.descTemplate() .replace("{player}", playerName);
        int jitter = def.jitter() > 0 ? rng.nextInt(def.jitter() * 2 + 1) - def.jitter() : 0;
        int value  = def.baseValue() + jitter;
        return new WeeklyEvent(
            week, season,
            def.eventType(), def.key(),
            title, desc,
            target,
            def.effectType(), def.effectTarget(), value
        );
    }

    // ════════════════════════════════════════════════════════════
    // 効果適用（既存コードそのまま）
    // ════════════════════════════════════════════════════════════

    private void applyEffect(WeeklyEvent event, Club club) {
        switch (event.getEffectType()) {
            case STAT_UP, STAT_DOWN -> {
                if (event.getTargetPlayer() != null)
                    event.applyToPlayer(event.getTargetPlayer());
            }
            case ALL_STAT_UP, ALL_STAT_DOWN ->
                event.applyToAllPlayers(club.getSquad());
            case BUDGET_CHANGE ->
                club.setBudget(club.getBudget() + event.getEffectValue());
            case NONE -> { }
        }
    }

    // ════════════════════════════════════════════════════════════
    // DB保存
    // ════════════════════════════════════════════════════════════

    private void saveEmotions(List<Player> players) {
        if (dbConn == null) return;
        try {
            new EmotionStateDao(dbConn).saveAll(players);
        } catch (SQLException e) {
            System.err.println("[EmotionState] DB保存エラー: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════
    // ヘルパー（既存コードそのまま + 感情考慮追加）
    // ════════════════════════════════════════════════════════════

    private boolean matchesSeason(String seasons, SeasonManager.Season current) {
        if ("ALL".equals(seasons)) return true;
        for (String tok : seasons.split(",")) {
            if (tok.trim().equals(current.name())) return true;
        }
        return false;
    }

    /** 状況に応じてイベントの重みを変える（感情パラメータ考慮を追加） */
    private int calcWeight(EventDef d, Club club, SeasonManager.Season season) {
        int base = 3;

        // 既存ロジック
        if (d.eventType() == EventType.CLUB_POSITIVE && club.getBudget() < 200_000_000) base = 4;
        if (d.key().equals("TEAM_MORALE_DOWN") && club.getLosses() > club.getWins()) base = 5;
        if (d.eventType() == EventType.NONE) base = 2;

        // 感情パラメータ考慮（NEW）
        // team_harmony が低いとネガティブイベント増加
        if (d.eventType() == EventType.CLUB_NEGATIVE && club.getTeamHarmony() < 30) base += 2;
        // team_harmony が高いとポジティブイベント増加
        if (d.eventType() == EventType.CLUB_POSITIVE && club.getTeamHarmony() > 70) base += 1;

        return base;
    }

    /** 対象選手を選択（frustration/slump 考慮を追加） */
    private Player selectTarget(EventType type, List<Player> squad) {
        if (squad == null || squad.isEmpty()) return null;
        if (type == EventType.CLUB_POSITIVE || type == EventType.CLUB_NEGATIVE
                || type == EventType.WEATHER || type == EventType.RIVAL_NEWS
                || type == EventType.NONE) return null;

        List<Player> active = squad.stream()
            .filter(p -> p.getRole() != Player.PlayerRole.RETIRED)
            .toList();
        if (active.isEmpty()) return null;

        if (type == EventType.PLAYER_NEGATIVE) {
            // ネガティブ → frustration が高い選手を優先して選びやすくする
            List<Player> frustrated = active.stream()
                .filter(p -> p.getEmotion().getFrustration() > 50)
                .toList();
            if (!frustrated.isEmpty() && rng.nextDouble() < 0.6) {
                return frustrated.get(rng.nextInt(frustrated.size()));
            }
        }

        if (type == EventType.PLAYER_POSITIVE) {
            // ポジティブ → slump中の選手をやや優先（回復チャンスを作る）
            List<Player> slumping = active.stream()
                .filter(p -> p.getEmotion().getMood() == Mood.SLUMP
                          || p.getEmotion().getMood() == Mood.DOWN)
                .toList();
            if (!slumping.isEmpty() && rng.nextDouble() < 0.4) {
                return slumping.get(rng.nextInt(slumping.size()));
            }
        }

        return active.get(rng.nextInt(active.size()));
    }

    private SeasonManager.Season toSeason(int week) {
        if (week <= 13)  return SeasonManager.Season.SPRING;
        if (week <= 26)  return SeasonManager.Season.SUMMER;
        if (week <= 39)  return SeasonManager.Season.AUTUMN;
        return SeasonManager.Season.WINTER;
    }
}
