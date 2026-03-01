package service;

import db.WeeklyEventDao;
import model.*;
import model.WeeklyEvent.*;

import java.sql.SQLException;
import java.util.*;

/**
 * 週次ランダムイベント生成・適用サービス
 *
 * 【呼び出しタイミング】
 *   WeeklyPlanView.executeAdvance() の advanceWeek() 直前に
 *   generateAndApply(club, week, season) を呼ぶ。
 *
 * 【イベント抽選ロジック】
 *   1. 季節と週番号でイベント定義をフィルタリング
 *   2. EventType ごとの重みで抽選
 *   3. 対象選手は position/OVR で重み付き抽選
 *   4. effectValue は定義値 ± ランダムジッター
 */
public class WeeklyEventService {

    private final Random          rng = new Random();
    private final WeeklyEventDao  dao = new WeeklyEventDao();

    // ── イベント定義 ─────────────────────────────────────────────
    private record EventDef(
        String    key,
        EventType eventType,
        String    titleTemplate,    // {player} を選手名に置換
        String    descTemplate,     // {player} を選手名に置換
        EffectType effectType,
        String    effectTarget,
        int       baseValue,        // 変動量の基本値（実際は ±jitter）
        int       jitter,           // ランダム幅（0 なら固定）
        String    seasons           // "ALL" or "SPRING,SUMMER" etc.
    ) {}

    private static final List<EventDef> DEFS = List.of(
        // ── 選手ポジティブ ─────────────────────────────────────
        new EventDef("TRAINING_INSPIRED",
            EventType.PLAYER_POSITIVE,
            "✨ {player} が練習で開眼！",
            "{player}が練習中に突然コツをつかみ、目をキラキラさせながら走り回った！スピードと全身能力が向上！",
            EffectType.STAT_UP, "speed", 4, 2, "ALL"),

        new EventDef("SHOOTING_PRACTICE",
            EventType.PLAYER_POSITIVE,
            "🎯 {player} のシュート特訓大成功！",
            "居残り特訓で何百本もシュートを蹴り続けた{player}。今やゴール前では怖いものなし！",
            EffectType.STAT_UP, "shooting", 5, 2, "ALL"),

        new EventDef("PASSING_VISION",
            EventType.PLAYER_POSITIVE,
            "👁 {player} のパスセンス覚醒！",
            "{player}が仲間の動きをまるで予知するかのような神業パスを連発し始めた！",
            EffectType.STAT_UP, "passing", 5, 2, "SPRING,AUTUMN"),

        new EventDef("DEFENSE_WALL",
            EventType.PLAYER_POSITIVE,
            "🛡 {player} が鉄壁の守備を習得！",
            "{player}が最終ラインで吠え続けた結果、相手FWが恐れて近づかなくなった！",
            EffectType.STAT_UP, "defending", 5, 2, "AUTUMN,WINTER"),

        new EventDef("PUPPY_ENERGY",
            EventType.PLAYER_POSITIVE,
            "🐶 {player} の仔犬パワーが炸裂！",
            "春の陽気で{player}のスタミナが大爆発！試合後もハイテンションで走り続けている！",
            EffectType.STAT_UP, "stamina", 6, 2, "SPRING"),

        new EventDef("SPIRIT_UP",
            EventType.PLAYER_POSITIVE,
            "🔥 {player} に根性の火が付いた！",
            "サポーターからの熱い応援メッセージを読んだ{player}。尻尾をぶんぶん振りながら練習に励む！",
            EffectType.STAT_UP, "spirit", 6, 2, "ALL"),

        new EventDef("VETERAN_WISDOM",
            EventType.PLAYER_POSITIVE,
            "🎓 {player} の老犬の知恵が光る！",
            "ベテランの{player}が若手に自分の経験を丁寧に伝授。その過程で自身の能力も再確認！",
            EffectType.STAT_UP, "passing", 4, 1, "ALL"),

        new EventDef("GOOD_MEAL",
            EventType.PLAYER_POSITIVE,
            "🍖 {player} が特製ごはんで体力回復！",
            "栄養士が考案した特製ドッグフードを食べた{player}のスタミナが見る見る回復していった！",
            EffectType.STAT_UP, "stamina", 5, 2, "ALL"),

        // ── 選手ネガティブ ─────────────────────────────────────
        new EventDef("INJURY_MINOR",
            EventType.PLAYER_NEGATIVE,
            "🤕 {player} が軽傷を負ってしまった…",
            "{player}が練習中に足を少し引っかけてしまった。暫くスタミナが落ちそうだ…",
            EffectType.STAT_DOWN, "stamina", -7, 2, "ALL"),

        new EventDef("OVEREATING",
            EventType.PLAYER_NEGATIVE,
            "🍖 {player} が食べすぎ注意報！",
            "{player}が冬のご褒美おやつを食べすぎてしまった。走るのが少し辛そう…",
            EffectType.STAT_DOWN, "speed", -4, 1, "WINTER"),

        new EventDef("SLUMP",
            EventType.PLAYER_NEGATIVE,
            "😞 {player} がスランプに突入…",
            "{player}のシュートが急に枠を外れ始めた。耳が垂れ下がりっぱなしで元気がない…",
            EffectType.STAT_DOWN, "shooting", -5, 2, "ALL"),

        new EventDef("DISTRACTED",
            EventType.PLAYER_NEGATIVE,
            "🦋 {player} がちょうちょに夢中！",
            "{player}が練習中に飛んできたちょうちょを追いかけて集中力が散漫に！精神力が落ちた…",
            EffectType.STAT_DOWN, "spirit", -5, 2, "SPRING,SUMMER"),

        new EventDef("COLD",
            EventType.PLAYER_NEGATIVE,
            "🤧 {player} が体調を崩してしまった…",
            "{player}が夏バテ（または冬の風邪）で体調不良。早い回復を祈ろう…",
            EffectType.STAT_DOWN, "stamina", -6, 2, "SUMMER,WINTER"),

        new EventDef("HOMESICK",
            EventType.PLAYER_NEGATIVE,
            "🏠 {player} がホームシック気味…",
            "{player}が故郷の家族を想って夜泣きをしているらしい。精神面に影響が出ている…",
            EffectType.STAT_DOWN, "spirit", -4, 1, "ALL"),

        new EventDef("DISTRACTION_SQUIRREL",
            EventType.PLAYER_NEGATIVE,
            "🐿 {player} がリスを発見して暴走！",
            "練習中にリスを見つけた{player}がグラウンドを飛び出してしまった！守備が疎かに…",
            EffectType.STAT_DOWN, "defending", -4, 2, "SUMMER,AUTUMN"),

        // ── クラブポジティブ ───────────────────────────────────
        new EventDef("SPONSOR_BONUS",
            EventType.CLUB_POSITIVE,
            "💰 スポンサーから追加ボーナス！",
            "地元企業が犬テーマのサッカーに感動してスポンサー契約を追加！クラブに臨時収入！",
            EffectType.BUDGET_CHANGE, "budget", 3_000_000, 1_000_000, "ALL"),

        new EventDef("MEDIA_BUZZ",
            EventType.CLUB_POSITIVE,
            "📺 メディアが大注目！グッズ爆売れ！",
            "テレビ局が犬サッカーの特集を組んでくれた！グッズ売上が急増し収益がUP！",
            EffectType.BUDGET_CHANGE, "budget", 1_500_000, 500_000, "ALL"),

        new EventDef("COMMUNITY_EVENT",
            EventType.CLUB_POSITIVE,
            "🎪 地域犬サッカー体験教室が大成功！",
            "子どもたちが大喜びした犬サッカー体験教室。笑顔と共にクラブに収益が入ってきた！",
            EffectType.BUDGET_CHANGE, "budget", 1_000_000, 300_000, "SPRING,SUMMER"),

        new EventDef("TEAM_MORALE_UP",
            EventType.CLUB_POSITIVE,
            "🎉 BBQパーティーでチームが結束！",
            "クラブ主催のBBQパーティーで選手全員が大はしゃぎ！チーム全体の精神力が向上！",
            EffectType.ALL_STAT_UP, "spirit", 3, 1, "ALL"),

        // ── クラブネガティブ ───────────────────────────────────
        new EventDef("EQUIPMENT_DAMAGE",
            EventType.CLUB_NEGATIVE,
            "🔧 設備の修繕費が発生…",
            "ある選手が練習場の設備を壊してしまった（名前は伏せます）！修繕費が必要に…",
            EffectType.BUDGET_CHANGE, "budget", -500_000, 100_000, "ALL"),

        new EventDef("FINE",
            EventType.CLUB_NEGATIVE,
            "📋 リーグから反則金が…",
            "先週の試合での過激なプレーにリーグから罰金が科された！クラブ運営に打撃…",
            EffectType.BUDGET_CHANGE, "budget", -300_000, 100_000, "ALL"),

        new EventDef("TEAM_MORALE_DOWN",
            EventType.CLUB_NEGATIVE,
            "😔 チームの士気が低下…",
            "連敗続きの影響でロッカールームの雰囲気が重い…。全選手の精神力に影響が出ている。",
            EffectType.ALL_STAT_DOWN, "spirit", -3, 1, "ALL"),

        // ── 天気・季節 ─────────────────────────────────────────
        new EventDef("HEATWAVE",
            EventType.WEATHER,
            "☀️ 猛暑日！犬には辛い季節…",
            "記録的な猛暑で練習を短縮！犬は暑さに弱い！全選手のスタミナに影響が出た…",
            EffectType.ALL_STAT_DOWN, "stamina", -3, 1, "SUMMER"),

        new EventDef("PERFECT_WEATHER",
            EventType.WEATHER,
            "🌸 絶好のコンディション！",
            "さわやかな気候で練習が絶好調！全選手が気持ちよく走り回っている！",
            EffectType.ALL_STAT_UP, "stamina", 3, 1, "SPRING,AUTUMN"),

        new EventDef("RAINY_SEASON",
            EventType.WEATHER,
            "🌧 長雨でグラウンド不良…",
            "長雨でグラウンドがぬかるみ、練習の質が落ちてしまった…全体的に影響が出ている。",
            EffectType.ALL_STAT_DOWN, "stamina", -2, 1, "SUMMER"),

        new EventDef("FIRST_SNOW",
            EventType.WEATHER,
            "❄️ 初雪に選手たちが大興奮！",
            "初雪に大興奮した選手たちが雪の中を駆け回った！その結果、精神力が大幅UP！",
            EffectType.ALL_STAT_UP, "spirit", 4, 1, "WINTER"),

        new EventDef("CHERRY_BLOSSOM",
            EventType.WEATHER,
            "🌸 桜の下でお花見練習！",
            "満開の桜の下でお花見練習会を開催！選手たちのテンションが最高潮に！",
            EffectType.ALL_STAT_UP, "spirit", 3, 1, "SPRING"),

        // ── ライバル情報 ───────────────────────────────────────
        new EventDef("RIVAL_INJURY",
            EventType.RIVAL_NEWS,
            "🏥 ライバルの主力が負傷との情報！",
            "ライバルクラブのエース格が負傷しているとの情報が入ってきた！チャンスが広がった！",
            EffectType.ALL_STAT_UP, "spirit", 2, 1, "ALL"),

        new EventDef("RIVAL_TRANSFER",
            EventType.RIVAL_NEWS,
            "⚡ ライバルが大物選手を獲得！",
            "強豪クラブが噂の大物選手を獲得したという情報が流れ…選手たちが少し不安そうだ。",
            EffectType.ALL_STAT_DOWN, "spirit", -2, 1, "ALL"),

        // ── 追加: 犬テーマ新イベント ──────────────────────────
        new EventDef("DREAM_BONE",
            EventType.PLAYER_POSITIVE,
            "🦴 {player} が夢の中で骨を発見！",
            "{player}が昼寝中に足をバタバタさせながら夢を見ていた！夢の中で練習した動きが体に染み込んだ！",
            EffectType.STAT_UP, "speed", 3, 1, "ALL"),

        new EventDef("FAN_LETTER",
            EventType.PLAYER_POSITIVE,
            "💌 {player} にファンレターが届いた！",
            "子どものサポーターから手書きのファンレターが届いた！感動して涙ぐんだ{player}の自信がみなぎった！",
            EffectType.STAT_UP, "spirit", 5, 2, "ALL"),

        new EventDef("AGILITY_TRAINING",
            EventType.PLAYER_POSITIVE,
            "🐾 {player} がアジリティ訓練で大躍進！",
            "障害物コースでベストタイムを叩き出した！{player}の俊敏性が大幅アップ！",
            EffectType.STAT_UP, "speed", 6, 2, "SPRING,SUMMER"),

        new EventDef("NEW_TOY",
            EventType.PLAYER_POSITIVE,
            "🧸 {player} に新しいおもちゃ！",
            "マネージャーがプレゼントした新しいおもちゃに{player}が大興奮！遊びの中で俊敏性が向上！",
            EffectType.STAT_UP, "speed", 4, 2, "ALL"),

        new EventDef("RIVAL_MOCKERY",
            EventType.PLAYER_NEGATIVE,
            "😤 {player} がライバルにバカにされた！",
            "ライバル選手に「犬ごときが」と言われてしまった{player}…傷ついてはいるが、怒りのエネルギーにもなりそう。",
            EffectType.STAT_DOWN, "spirit", -3, 1, "ALL"),

        new EventDef("THUNDER_SCARED",
            EventType.PLAYER_NEGATIVE,
            "⚡ {player} が雷に驚いて隠れた！",
            "嵐の夜に雷が鳴り響き、{player}は毛布の中から出てこなかった…スタミナが消耗してしまった。",
            EffectType.STAT_DOWN, "stamina", -4, 2, "SUMMER,WINTER"),

        new EventDef("BATH_REFUSAL",
            EventType.PLAYER_NEGATIVE,
            "🛁 {player} がお風呂を拒否！",
            "お風呂の時間になると{player}が逃げ回ってしまい、コンディション維持に支障が…",
            EffectType.STAT_DOWN, "stamina", -3, 1, "ALL"),

        new EventDef("FULL_MOON_HOWL",
            EventType.CLUB_POSITIVE,
            "🌕 満月の夜にチーム全員で遠吠え！",
            "満月の夜、グラウンドに集まって全員で遠吠え！奇妙な儀式でチームの絆が深まった！",
            EffectType.ALL_STAT_UP, "spirit", 4, 1, "ALL"),

        new EventDef("DOGPARK_OUTING",
            EventType.CLUB_POSITIVE,
            "🌳 チームでドッグランに遠足！",
            "練習の合間にドッグランへ！自由に走り回ったことでチーム全体のスタミナが回復！",
            EffectType.ALL_STAT_UP, "stamina", 3, 1, "SPRING,SUMMER,AUTUMN"),

        new EventDef("NAUGHTY_CHEWING",
            EventType.CLUB_NEGATIVE,
            "😫 選手がユニフォームを噛んでしまった！",
            "誰かがゴール裏に置いてあったユニフォームをかじってしまった（名前は伏せます）！修繕費発生…",
            EffectType.BUDGET_CHANGE, "budget", -200_000, 50_000, "ALL"),

        // ── 平穏な週 ───────────────────────────────────────────
        new EventDef("PEACEFUL_WEEK",
            EventType.NONE,
            "☀️ 今週は穏やかな一週間でした",
            "特に大きな出来事もなく、選手たちは黙々と練習に励んでいる。これが一番だ！",
            EffectType.NONE, "", 0, 0, "ALL")
    );

    // ── メインメソッド ───────────────────────────────────────────
    /**
     * イベントを1つ生成・適用・DB保存して返す。
     * WeeklyPlanView から毎週呼ぶ。
     */
    public WeeklyEvent generateAndApply(Club club, int week, int season) {
        SeasonManager.Season currentSeason = toSeason(week);
        WeeklyEvent event = generate(club, week, season, currentSeason);
        applyEffect(event, club);

        // 仲良し度: イベント対象選手 + チーム全体にポイント付与
        List<String> friendMsgs = ui.MainApp.friendshipManager.recordEvent(
            event.getTargetPlayer(), club.getSquad());
        if (!friendMsgs.isEmpty()) {
            System.out.println("[Friendship] " + String.join(" / ", friendMsgs));
        }

        try {
            dao.insert(event);
        } catch (SQLException e) {
            System.err.println("[WeeklyEvent] DB保存エラー: " + e.getMessage());
        }
        return event;
    }

    // ── 生成ロジック ─────────────────────────────────────────────
    private WeeklyEvent generate(Club club, int week, int season,
                                  SeasonManager.Season currentSeason) {
        // 季節でフィルタしたdef一覧を構築（重み付き）
        List<EventDef> candidates = new ArrayList<>();
        for (EventDef d : DEFS) {
            if (matchesSeason(d.seasons(), currentSeason)) {
                int weight = calcWeight(d, club, currentSeason);
                for (int i = 0; i < weight; i++) candidates.add(d);
            }
        }
        if (candidates.isEmpty()) candidates.addAll(DEFS); // フォールバック

        EventDef def = candidates.get(rng.nextInt(candidates.size()));

        // 対象選手の決定
        Player target = selectTarget(def.eventType(), club.getSquad());
        String playerName = target != null ? target.getFullName() : "チーム";

        String title = def.titleTemplate().replace("{player}", playerName);
        String desc  = def.descTemplate() .replace("{player}", playerName);

        // effectValue = baseValue ± jitter
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

    // ── 効果適用 ─────────────────────────────────────────────────
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
            case NONE -> { /* 演出のみ */ }
        }
    }

    // ── ヘルパー ─────────────────────────────────────────────────
    private boolean matchesSeason(String seasons, SeasonManager.Season current) {
        if ("ALL".equals(seasons)) return true;
        String s = current.name();
        // SUMMER ← 梅雨もSUMMERに含める
        for (String tok : seasons.split(",")) {
            if (tok.trim().equals(s)) return true;
        }
        return false;
    }

    /** 状況に応じてイベントの重みを変える */
    private int calcWeight(EventDef d, Club club, SeasonManager.Season season) {
        int base = 3;
        // 低予算クラブはクラブポジ(スポンサー)が若干出やすい
        if (d.eventType() == EventType.CLUB_POSITIVE && club.getBudget() < 200_000_000) base = 4;
        // 連敗中は士気系ネガが出やすい
        if (d.key().equals("TEAM_MORALE_DOWN") && club.getLosses() > club.getWins()) base = 5;
        // 平穏な週は常に低め
        if (d.eventType() == EventType.NONE) base = 2;
        return base;
    }

    /** イベントタイプに合った対象選手を抽選 */
    private Player selectTarget(EventType type, List<Player> squad) {
        if (squad == null || squad.isEmpty()) return null;
        if (type == EventType.CLUB_POSITIVE || type == EventType.CLUB_NEGATIVE
                || type == EventType.WEATHER || type == EventType.RIVAL_NEWS
                || type == EventType.NONE) return null;

        // ポジティブ → OVRが低い選手に成長余地があるので少し高重み
        // ネガティブ → OVRが高い選手は影響が目立つので均等
        return squad.get(rng.nextInt(squad.size()));
    }

    private SeasonManager.Season toSeason(int week) {
        if (week <= 13)  return SeasonManager.Season.SPRING;
        if (week <= 26)  return SeasonManager.Season.SUMMER;
        if (week <= 39)  return SeasonManager.Season.AUTUMN;
        return SeasonManager.Season.WINTER;
    }
}
