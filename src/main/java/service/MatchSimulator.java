package service;

import model.*;
import model.MatchEvent.Type;

import java.util.*;

/**
 * 試合シミュレーター
 * ・90分の全イベントを生成
 * ・各イベントに「選手位置リスト」を含める（背番号表示に使用）
 * ・GOALイベント発生時、発動者に必殺技があれば SPECIAL_MOVE イベントを挿入
 */
public class MatchSimulator {

    private static final int TOTAL_MINUTES = 90;
    private final Random rng = new Random();

    // ── コメント定義 ─────────────────────────────────────────
    private static final String[] GOAL_COMMENTS = {
        "ゴォォール！！🐾 スタジアムが吠え声で揺れる！",
        "決めた！ワンワンワン！🐕 尻尾が高速回転！",
        "見事なシュート！犬耳がぴん！",
        "ネットを揺らした！🎯 お手柄すぎてお手を要求してる！",
        "ゴール！🐾 ベンチの犬たちも全員立ち上がって吠えてる！"
    };
    private static final String[] SHOT_COMMENTS = {
        "シュート！鼻先で狙いを定めた！", "強烈な一撃！前足フルスイング！",
        "ゴールを狙う！耳が後ろに倒れてる！"
    };
    private static final String[] SAVE_COMMENTS = {
        "GKが飛びついてナイスセーブ！🐾",
        "体を張って止めた！前足を広げてブロック！",
        "ナイスセーブ！🧤 ボールを鼻でいなした！？"
    };
    private static final String[] MISS_COMMENTS = {
        "枠を外れた…前足に力が入りすぎた！",
        "惜しい！ポストに当たった！ワン！",
        "決定機を逃した…しょんぼり😢"
    };
    private static final String[] CHANCE_COMMENTS = {
        "決定的なチャンス！全速力で駆け込む！",
        "抜け出した！速い！速すぎる！🐕",
        "フリーでシュートチャンス！犬歯を光らせて向かう！"
    };
    private static final String[] FOUL_COMMENTS = {
        "ファウル！思わず噛みついた！？",
        "危険なタックル！相手の尻尾を踏んでしまった！",
        "ファウル！興奮しすぎて飛びかかった！🟨"
    };
    private static final String[] CORNER_COMMENTS = {
        "コーナーキック！🚩 全員がゴール前に集まる！",
        "コーナーへ！頭を使うか前足を使うか！"
    };
    private static final String[] PASS_COMMENTS = {
        "パスをつなぐ🐾", "ドリブルで前進！",
        "素早い展開！チームワーク抜群！", "パス回しが流れるようだ🎶"
    };
    private static final String[] COUNTER_COMMENTS = {
        "カウンター！一気に駆け上がる！🐕",
        "速攻！前足を全力で動かして突破！"
    };
    private static final String[] PRESS_COMMENTS = {
        "プレスをかける！群れで追い込む狼戦術！",
        "激しいプレッシング！🐾"
    };

    // ── 選手位置データ ────────────────────────────────────────

    /**
     * 選手位置情報（ピッチ描画用）
     * x, y は 0.0〜1.0 の正規化座標（x: 左→右, y: 上→下）
     */
    public record PlayerPosition(int shirtNumber, double x, double y, boolean isHome) {}

    /**
     * フォーメーション別の基本ポジション（4-3-3）
     * 攻撃方向: ホームは右向き(x: 0→1)、アウェイは左向き
     */
    private static final double[][] BASE_POSITIONS_433 = {
        // GK, DF×4, MF×3, FW×3  (x=0.0が自陣ゴール, x=1.0が相手ゴール)
        {0.05, 0.50},  // GK  #1
        {0.20, 0.20},  // RB  #2
        {0.20, 0.38},  // CB  #3 / #4
        {0.20, 0.62},
        {0.20, 0.80},  // LB  #5
        {0.45, 0.30},  // MF  #6 / #7 / #8
        {0.45, 0.50},
        {0.45, 0.70},
        {0.72, 0.25},  // FW  #9 / #10 / #11
        {0.72, 0.50},
        {0.72, 0.75},
    };

    /**
     * 試合中の選手位置を生成する。
     * ballX に応じてフォーメーションを動的にシフトさせる。
     */
    private List<PlayerPosition> generatePositions(Club home, Club away,
                                                    double ballX, double ballY,
                                                    boolean homeHasBall) {
        List<PlayerPosition> positions = new ArrayList<>();
        List<Player> homeSquad = home.getSquad();
        List<Player> awaySquad = away.getSquad();

        // ホームチームの攻守に応じてラインを動かす
        double homeShift = homeHasBall ? 0.15 : -0.05;
        double awayShift = homeHasBall ? 0.05 : -0.15;

        for (int i = 0; i < Math.min(11, homeSquad.size()); i++) {
            Player p = homeSquad.get(i);
            double[] base = BASE_POSITIONS_433[i];
            double jitter = (rng.nextDouble() - 0.5) * 0.06;  // 少しランダムに動かす
            double px = Math.max(0.03, Math.min(0.97, base[0] + homeShift + jitter));
            double py = Math.max(0.05, Math.min(0.95, base[1] + jitter));
            positions.add(new PlayerPosition(p.getShirtNumber(), px, py, true));
        }
        for (int i = 0; i < Math.min(11, awaySquad.size()); i++) {
            Player p = awaySquad.get(i);
            double[] base = BASE_POSITIONS_433[i];
            double jitter = (rng.nextDouble() - 0.5) * 0.06;
            // アウェイは左向き（x座標を反転）
            double px = Math.max(0.03, Math.min(0.97, (1.0 - base[0]) + awayShift + jitter));
            double py = Math.max(0.05, Math.min(0.95, base[1] + jitter));
            positions.add(new PlayerPosition(p.getShirtNumber(), px, py, false));
        }
        return positions;
    }

    // ── メインシミュレーション ────────────────────────────────

    public List<MatchEvent> simulateEvents(Club home, Club away) {
        List<MatchEvent> events = new ArrayList<>();

        double homeOvr = home.getAverageOverall() * 1.05;
        double awayOvr = away.getAverageOverall();

        int homeScore = 0, awayScore = 0, homePoss = 50;
        int[] shots = {0,0}, shotsOn = {0,0}, corners = {0,0}, fouls = {0,0};

        // キックオフ
        List<PlayerPosition> kickoffPos = generatePositions(home, away, 0.5, 0.5, true);
        events.add(new MatchEvent(0, Type.KICKOFF,
            "キックオフ！🐾 " + home.getName() + " vs " + away.getName(),
            true, 0.5, 0.5, 0, 0, 50, shots, shotsOn, corners, fouls,
            null, null));
        setPositions(events.get(events.size()-1), kickoffPos);

        for (int min = 1; min <= TOTAL_MINUTES; min++) {
            double stamina = min > 60 ? 0.92 : 1.0;
            double homePossChance = homeOvr * stamina;
            double awayPossChance = awayOvr * stamina;
            boolean homeHasBall = rng.nextDouble() < homePossChance / (homePossChance + awayPossChance);
            int side = homeHasBall ? 0 : 1;

            if (homeHasBall) homePoss = Math.min(80, homePoss + rng.nextInt(3));
            else             homePoss = Math.max(20, homePoss - rng.nextInt(3));

            double ballX = homeHasBall ? 0.5 + rng.nextDouble()*0.4 : 0.1 + rng.nextDouble()*0.4;
            double ballY = 0.2 + rng.nextDouble()*0.6;

            List<PlayerPosition> pos = generatePositions(home, away, ballX, ballY, homeHasBall);

            Club attTeam = homeHasBall ? home : away;
            Club defTeam = homeHasBall ? away : home;
            double attOvr = homeHasBall ? homeOvr : awayOvr;
            double defOvr = homeHasBall ? awayOvr : homeOvr;

            if (min == 45) {
                MatchEvent ht = new MatchEvent(45, Type.HALFTIME,
                    "ハーフタイム🐾 前半スコア: " + homeScore + " - " + awayScore,
                    true, 0.5, 0.5, homeScore, awayScore, homePoss,
                    shots, shotsOn, corners, fouls, null, null);
                setPositions(ht, pos);
                events.add(ht);
                continue;
            }

            if (chance(8)) {
                fouls[1-side]++;
                MatchEvent fe = new MatchEvent(min, Type.FOUL,
                    defTeam.getName() + "が" + pick(FOUL_COMMENTS),
                    !homeHasBall, ballX, ballY, homeScore, awayScore, homePoss,
                    shots, shotsOn, corners, fouls, null, null);
                setPositions(fe, pos); events.add(fe);
                if (chance(30)) {
                    corners[side]++;
                    MatchEvent ce = new MatchEvent(min, Type.CORNER, pick(CORNER_COMMENTS),
                        homeHasBall, ballX, ballY, homeScore, awayScore, homePoss,
                        shots, shotsOn, corners, fouls, null, null);
                    setPositions(ce, pos); events.add(ce);
                }
                continue;
            }

            if (chance(5)) {
                MatchEvent ctr = new MatchEvent(min, Type.COUNTER,
                    attTeam.getName() + pick(COUNTER_COMMENTS),
                    homeHasBall, homeHasBall ? 0.8 : 0.2, 0.5,
                    homeScore, awayScore, homePoss, shots, shotsOn, corners, fouls,
                    null, null);
                setPositions(ctr, pos); events.add(ctr);
            }

            double chanceProb = 10 + (attOvr / defOvr) * 8;
            if (chance(chanceProb)) {
                Player scorer = getScorer(attTeam);
                MatchEvent ch = new MatchEvent(min, Type.CHANCE,
                    attTeam.getName() + pick(CHANCE_COMMENTS),
                    homeHasBall, homeHasBall ? 0.82 : 0.18, 0.5,
                    homeScore, awayScore, homePoss, shots, shotsOn, corners, fouls,
                    scorer, null);
                setPositions(ch, pos); events.add(ch);

                double shotProb = 45 + (attOvr / defOvr) * 20;
                if (chance(shotProb)) {
                    shots[side]++;
                    MatchEvent sh = new MatchEvent(min, Type.SHOT,
                        attTeam.getName() + pick(SHOT_COMMENTS),
                        homeHasBall, homeHasBall ? 0.9 : 0.1, 0.5,
                        homeScore, awayScore, homePoss, shots, shotsOn, corners, fouls,
                        scorer, null);
                    setPositions(sh, pos); events.add(sh);

                    if (chance(45)) {
                        shotsOn[side]++;
                        // 必殺技発動チェック（シュート枠内時に30%の確率で発動）
                        SpecialMove activeMove = null;
                        if (scorer != null && scorer.hasSpecialMove() && chance(30)) {
                            activeMove = scorer.getSpecialMove();
                            // 必殺技イベントを挿入（カットインで表示される）
                            MatchEvent sm = new MatchEvent(min, Type.SPECIAL_MOVE,
                                "🌟 必殺技発動！【" + activeMove.getName() + "】",
                                homeHasBall, homeHasBall ? 0.92 : 0.08, 0.5,
                                homeScore, awayScore, homePoss,
                                shots, shotsOn, corners, fouls,
                                scorer, activeMove);
                            setPositions(sm, pos); events.add(sm);
                        }

                        // ゴール確率（必殺技があれば威力分UP）
                        double goalProb = 22 + (attOvr - defOvr) * 0.5
                            + (activeMove != null ? activeMove.getPower() * 0.15 : 0);
                        if (chance(goalProb)) {
                            if (homeHasBall) homeScore++; else awayScore++;
                            MatchEvent ge = new MatchEvent(min, Type.GOAL,
                                "⚽ ゴール！！ " + attTeam.getName() + "が得点！【"
                                + (scorer != null ? scorer.getFullName() : "🐾") + "】",
                                homeHasBall, homeHasBall ? 0.98 : 0.02, 0.5,
                                homeScore, awayScore, homePoss,
                                shots, shotsOn, corners, fouls,
                                scorer, null);
                            setPositions(ge, pos); events.add(ge);
                        } else {
                            MatchEvent sv = new MatchEvent(min, Type.SAVE,
                                defTeam.getName() + "GK " + pick(SAVE_COMMENTS),
                                !homeHasBall, homeHasBall ? 0.98 : 0.02, 0.5,
                                homeScore, awayScore, homePoss,
                                shots, shotsOn, corners, fouls, null, null);
                            setPositions(sv, pos); events.add(sv);
                            if (chance(40)) {
                                corners[side]++;
                                MatchEvent co = new MatchEvent(min, Type.CORNER,
                                    pick(CORNER_COMMENTS), homeHasBall, ballX, ballY,
                                    homeScore, awayScore, homePoss,
                                    shots, shotsOn, corners, fouls, null, null);
                                setPositions(co, pos); events.add(co);
                            }
                        }
                    } else {
                        MatchEvent ms = new MatchEvent(min, Type.MISS,
                            pick(MISS_COMMENTS), homeHasBall, ballX, ballY,
                            homeScore, awayScore, homePoss,
                            shots, shotsOn, corners, fouls, null, null);
                        setPositions(ms, pos); events.add(ms);
                    }
                }
            } else if (chance(35)) {
                MatchEvent ps = new MatchEvent(min, Type.PASS,
                    attTeam.getName() + "が" + pick(PASS_COMMENTS),
                    homeHasBall, ballX, ballY, homeScore, awayScore, homePoss,
                    shots, shotsOn, corners, fouls, null, null);
                setPositions(ps, pos); events.add(ps);
            } else if (chance(25)) {
                MatchEvent pr = new MatchEvent(min, Type.PRESS,
                    defTeam.getName() + "が" + pick(PRESS_COMMENTS),
                    !homeHasBall, ballX, ballY, homeScore, awayScore, homePoss,
                    shots, shotsOn, corners, fouls, null, null);
                setPositions(pr, pos); events.add(pr);
            }
        }

        MatchEvent ft = new MatchEvent(90, Type.FULLTIME,
            "試合終了🐾 最終スコア: " + homeScore + " - " + awayScore,
            true, 0.5, 0.5, homeScore, awayScore, homePoss,
            shots, shotsOn, corners, fouls, null, null);
        setPositions(ft, generatePositions(home, away, 0.5, 0.5, true));
        events.add(ft);

        return events;
    }

    // 後方互換
    public Match simulate(Club home, Club away) {
        List<MatchEvent> events = simulateEvents(home, away);
        MatchEvent last = events.get(events.size()-1);
        Match match = new Match(home, away);
        match.setResult(last.getHomeScore(), last.getAwayScore());
        return match;
    }

    // ── 選手位置をイベントに添付（Mapで保持） ─────────────────
    // MatchEventに直接リストを持たせると既存コンストラクタが増えすぎるため
    // WeakHashMapで外部管理する
    private final Map<MatchEvent, List<PlayerPosition>> positionMap = new WeakHashMap<>();

    private void setPositions(MatchEvent evt, List<PlayerPosition> pos) {
        positionMap.put(evt, pos);
    }

    public List<PlayerPosition> getPositions(MatchEvent evt) {
        return positionMap.getOrDefault(evt, List.of());
    }

    // ── ヘルパー ─────────────────────────────────────────────
    private boolean chance(double pct) { return rng.nextDouble() * 100 < pct; }
    private String  pick(String[] arr) { return arr[rng.nextInt(arr.length)]; }

    private Player getScorer(Club club) {
        List<Player> squad = club.getSquad();
        if (squad.isEmpty()) return null;
        return squad.stream()
            .filter(p -> p.getPosition() == Player.Position.FW)
            .findFirst()
            .orElse(squad.get(rng.nextInt(squad.size())));
    }
}
