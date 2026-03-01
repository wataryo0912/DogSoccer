package service;

import model.*;
import model.EmotionState.Mood;

import java.util.*;
import java.util.stream.Collectors;

/**
 * クラブAI自律判断エンジン（Phase 3）
 *
 * 18クラブそれぞれが「思想（Philosophy）」を持ち、
 * 毎週・移籍ウィンドウ・シーズン開幕に自律的に判断を行う。
 *
 * ── 思想一覧 ────────────────────────────────────────────────
 *  DEFENSIVE_INTELLECT  守備的知性（守りを固めて勝ち点を積む）
 *  YOUTH_ACADEMY        育成重視（潜在能力の高い若手を育てる）
 *  GALACTICO_ATTACK     銀河系攻撃（高OVRのスター選手を集める）
 *  PHYSICAL_PRESS       フィジカルプレス（体力・スタミナ優先）
 *  MONEYBALL            データ主義（コスパ重視・統計で補強）
 *  PURE_DATA            純粋AI（感情を無視した純統計判断）
 *  BALANCED             バランス型（汎用的判断）
 *
 * ── 呼び出しタイミング ────────────────────────────────────
 *  毎週:         ClubAI.processWeek(club, week, allPlayers)
 *  移籍期間:     ClubAI.processTransferWindow(club, allClubs)
 *  シーズン開幕: ClubAI.processSeasonStart(club, allClubs)
 */
public class ClubAI {

    // ════════════════════════════════════════════════════════════
    // 思想定義
    // ════════════════════════════════════════════════════════════

    public enum Philosophy {
        DEFENSIVE_INTELLECT ("守備的知性"),
        YOUTH_ACADEMY       ("育成重視"),
        GALACTICO_ATTACK    ("銀河系攻撃"),
        PHYSICAL_PRESS      ("フィジカルプレス"),
        MONEYBALL           ("データ主義"),
        PURE_DATA           ("純粋AI"),
        BALANCED            ("バランス型");

        public final String label;
        Philosophy(String label) { this.label = label; }
    }

    // ── クラブ名 → 思想のマッピング ──────────────────────────
    private static final Map<String, Philosophy> CLUB_PHILOSOPHY = Map.ofEntries(
        Map.entry("老犬カンフーズ",          Philosophy.DEFENSIVE_INTELLECT),
        Map.entry("白影アカデミア",           Philosophy.YOUTH_ACADEMY),
        Map.entry("黒鎧フォートレス",         Philosophy.DEFENSIVE_INTELLECT),
        Map.entry("蒼海ユースアカデミー",     Philosophy.YOUTH_ACADEMY),
        Map.entry("氷壁タクティクス",         Philosophy.DEFENSIVE_INTELLECT),
        Map.entry("ノーザン・ロングラン",     Philosophy.PHYSICAL_PRESS),
        Map.entry("赤牙プレス団",             Philosophy.PHYSICAL_PRESS),
        Map.entry("サンフレア・フリーダム",   Philosophy.GALACTICO_ATTACK),
        Map.entry("闘魂バリオス",             Philosophy.PHYSICAL_PRESS),
        Map.entry("ゴールドスターズ",         Philosophy.GALACTICO_ATTACK),
        Map.entry("ロイヤル・ブレインズ",     Philosophy.MONEYBALL),
        Map.entry("シルク・ウィングス",       Philosophy.GALACTICO_ATTACK),
        Map.entry("山岳ファミリア",           Philosophy.BALANCED),
        Map.entry("グリーンハウンド",         Philosophy.YOUTH_ACADEMY),
        Map.entry("ストーンウォールズ",       Philosophy.DEFENSIVE_INTELLECT),
        Map.entry("都市型マネーボールFC",     Philosophy.MONEYBALL),
        Map.entry("メトロポリス・エナジー",   Philosophy.PHYSICAL_PRESS),
        Map.entry("シリコン・アルゴリズムズ", Philosophy.PURE_DATA)
    );

    private final Random rng = new Random();
    private final ScoutService scoutService = new ScoutService();

    // ════════════════════════════════════════════════════════════
    // 公開API
    // ════════════════════════════════════════════════════════════

    /**
     * 毎週の判断（advanceWeek の後に全AIクラブに対して呼ぶ）
     * - スカッドの役割見直し
     * - frustrated選手への対処
     * - team_harmonyの管理
     */
    public AIWeeklyDecision processWeek(Club club, int week) {
        Philosophy phi = getPhilosophy(club);
        AIWeeklyDecision decision = new AIWeeklyDecision(club.getName(), week);

        // ── ① frustrated選手への対処 ──────────────────────────
        handleFrustratedPlayers(club, phi, decision);

        // ── ② slump選手への対処 ───────────────────────────────
        handleSlumpPlayers(club, phi, decision);

        // ── ③ スカッドの役割見直し ────────────────────────────
        reviewSquadRoles(club, phi, decision);

        // ── ④ team_harmony 管理 ───────────────────────────────
        manageHarmony(club, phi, decision);

        return decision;
    }

    /**
     * 移籍ウィンドウ中の補強・放出判断
     * SeasonManager.isTransferWindowOpen() == true の週に呼ぶ
     */
    public AITransferDecision processTransferWindow(
            Club club, List<Club> allClubs, List<Player> freeAgents) {

        Philosophy phi = getPhilosophy(club);
        AITransferDecision decision = new AITransferDecision(club.getName());

        // ── 放出判断 ──────────────────────────────────────────
        List<Player> toRelease = decideRelease(club, phi);
        decision.releases.addAll(toRelease);
        for (Player p : toRelease) {
            club.getSquad().remove(p);
            p.setRole(Player.PlayerRole.INACTIVE);
        }

        // ── 補強判断 ──────────────────────────────────────────
        List<Player> targets = decideSignings(club, phi, freeAgents, allClubs);
        for (Player target : targets) {
            long fee = estimateTransferFee(target, phi);
            if (club.getBudget() >= fee + target.getSalary() * 4L) {
                club.setBudget(club.getBudget() - fee);
                club.getSquad().add(target);
                target.setRole(Player.PlayerRole.REGISTERED);
                decision.signings.add(new AITransferDecision.Signing(target, fee));
            }
        }

        return decision;
    }

    /**
     * シーズン開幕時の初期スカッド整備
     * startNewSeason() の後に呼ぶ
     */
    public void processSeasonStart(Club club, List<Club> allClubs) {
        Philosophy phi = getPhilosophy(club);

        // 引退選手をスカッドから除外
        club.getSquad().removeIf(
            p -> p.getRole() == Player.PlayerRole.RETIRED);

        // 下部組織からの自動昇格（育成重視クラブ優先）
        if (phi == Philosophy.YOUTH_ACADEMY || phi == Philosophy.BALANCED) {
            club.getAcademyPlayers().stream()
                .filter(p -> p.getOverall() >= 65)
                .forEach(p -> p.setRole(Player.PlayerRole.REGISTERED));
        }

        // スカッドが手薄な場合は自動補充
        if (club.getRegistered().size() < 8) {
            autoFillSquad(club, phi);
        }
    }

    // ════════════════════════════════════════════════════════════
    // 毎週処理の内部ロジック
    // ════════════════════════════════════════════════════════════

    /** frustrated選手への対処（思想ごとに異なる） */
    private void handleFrustratedPlayers(
            Club club, Philosophy phi, AIWeeklyDecision decision) {

        List<Player> frustrated = club.getSquad().stream()
            .filter(p -> p.getEmotion().getFrustration() >= 70
                      && p.getRole() != Player.PlayerRole.RETIRED)
            .toList();

        for (Player p : frustrated) {
            switch (phi) {
                case DEFENSIVE_INTELLECT, BALANCED -> {
                    // 個別面談 → frustration -20, loyalty +5
                    p.getEmotion().resolveFrustrationTalk();
                    decision.log(p.getFullName() + " と個別面談（不満-20）");
                }
                case YOUTH_ACADEMY -> {
                    // 昇格約束でモチベート → frustration -15
                    p.getEmotion().setFrustration(
                        Math.max(0, p.getEmotion().getFrustration() - 15));
                    decision.log(p.getFullName() + " に昇格を約束");
                }
                case GALACTICO_ATTACK -> {
                    // スター選手は放出も辞さない
                    if (p.getOverall() < 75) {
                        decision.flagForRelease(p,
                            "不満継続のため放出検討（銀河系方針）");
                    } else {
                        p.getEmotion().resolveFrustrationTalk();
                    }
                }
                case PHYSICAL_PRESS -> {
                    // 公開注意（強権的）→ frustration -10 だがharmony -8
                    p.getEmotion().resolveFrustrationPublic();
                    club.setTeamHarmony(club.getTeamHarmony() - 8);
                    decision.log(p.getFullName() + " を公開注意（harmony -8）");
                }
                case MONEYBALL -> {
                    // コスパ計算: 給与 vs 不満リスク
                    double costRisk = p.getSalary() / (double) p.getOverall();
                    if (costRisk > 15_000) {
                        decision.flagForRelease(p, "コスパ悪化のため放出検討");
                    } else {
                        p.getEmotion().resolveFrustrationTalk();
                    }
                }
                case PURE_DATA -> {
                    // 感情無視、統計だけ見る
                    // frustrationが高くてもOVRが高ければ放置
                    if (p.getOverall() < 70) {
                        decision.flagForRelease(p, "統計的に非効率な選手を放出検討");
                    }
                    // 感情パラメータへの介入なし
                }
            }
        }
    }

    /** slump選手への対処 */
    private void handleSlumpPlayers(
            Club club, Philosophy phi, AIWeeklyDecision decision) {

        List<Player> slumping = club.getSquad().stream()
            .filter(p -> p.getEmotion().getMood() == Mood.SLUMP
                      && p.getRole() != Player.PlayerRole.RETIRED)
            .toList();

        for (Player p : slumping) {
            switch (phi) {
                case YOUTH_ACADEMY, BALANCED, DEFENSIVE_INTELLECT -> {
                    // 相談セッション → 25%で回復
                    int hotline = p.getEmotion().getLoyalty();
                    p.getEmotion().resolveConsultation(hotline);
                    decision.log(p.getFullName() + " にスランプ相談セッション実施");
                }
                case PURE_DATA, MONEYBALL -> {
                    // 統計的非効率とみてベンチへ
                    if (p.getRole() == Player.PlayerRole.REGISTERED) {
                        p.setRole(Player.PlayerRole.BENCH);
                        decision.log(p.getFullName() + " をスランプでベンチへ降格");
                    }
                }
                case GALACTICO_ATTACK, PHYSICAL_PRESS -> {
                    // 放置（自力回復を期待）
                }
            }
        }
    }

    /** スカッドの役割見直し */
    private void reviewSquadRoles(
            Club club, Philosophy phi, AIWeeklyDecision decision) {

        // GKが登録から外れていたら最高OVRのGKを登録へ
        boolean hasRegisteredGK = club.getRegistered().stream()
            .anyMatch(p -> p.getPosition() == Player.Position.GK);
        if (!hasRegisteredGK) {
            club.getSquad().stream()
                .filter(p -> p.getPosition() == Player.Position.GK
                          && p.getRole() != Player.PlayerRole.RETIRED)
                .max(Comparator.comparingInt(Player::getOverall))
                .ifPresent(gk -> {
                    gk.setRole(Player.PlayerRole.REGISTERED);
                    decision.log(gk.getFullName() + " をGK枠で自動登録");
                });
        }

        // 育成重視: OVRが潜在能力の85%超えた若手を昇格
        if (phi == Philosophy.YOUTH_ACADEMY) {
            club.getAcademyPlayers().stream()
                .filter(p -> p.getAge() <= 22
                          && p.getOverall() >= p.getPotential() * 0.85)
                .forEach(p -> {
                    p.setRole(Player.PlayerRole.REGISTERED);
                    decision.log(p.getFullName() + " が育成ラインを突破し昇格");
                });
        }

        // 銀河系: OVR70未満の選手をベンチへ
        if (phi == Philosophy.GALACTICO_ATTACK) {
            club.getRegistered().stream()
                .filter(p -> p.getOverall() < 70 && !p.isCaptain())
                .forEach(p -> {
                    p.setRole(Player.PlayerRole.BENCH);
                    decision.log(p.getFullName() + " OVR不足でベンチへ（銀河系方針）");
                });
        }
    }

    /** team_harmony 管理 */
    private void manageHarmony(
            Club club, Philosophy phi, AIWeeklyDecision decision) {

        int harmony = club.getTeamHarmony();

        // ゴールデン系選手がいればharmony下限30を保護（EmotionEngineと二重保護）
        boolean hasGuardian = club.getSquad().stream()
            .anyMatch(p -> p.getBreedTrait().guardian
                       && p.getRole() == Player.PlayerRole.REGISTERED);
        if (hasGuardian && harmony < 30) {
            club.setTeamHarmony(30);
        }

        // harmony < 30 の緊急対応
        if (harmony < 30) {
            switch (phi) {
                case DEFENSIVE_INTELLECT, BALANCED, YOUTH_ACADEMY -> {
                    // チームビルディングイベントで回復
                    club.setTeamHarmony(Math.min(100, harmony + 15));
                    decision.log("チームビルディング実施（harmony +" + 15 + "）");
                }
                case PHYSICAL_PRESS -> {
                    // 締め付けでharmony維持（強引）
                    club.setTeamHarmony(Math.min(100, harmony + 8));
                }
                case PURE_DATA, MONEYBALL -> {
                    // 統計的に「harmony < 30 = 敗戦率+12%」と判断してコスト投入
                    club.setTeamHarmony(Math.min(100, harmony + 12));
                    decision.log("データ分析に基づきharmony改善施策を実施");
                }
                case GALACTICO_ATTACK -> {
                    // スターの個人能力で補う（harmonyは放置）
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════
    // 移籍ウィンドウの内部ロジック
    // ════════════════════════════════════════════════════════════

    /** 放出すべき選手リストを決定 */
    private List<Player> decideRelease(Club club, Philosophy phi) {
        return club.getSquad().stream()
            .filter(p -> shouldRelease(p, phi, club))
            .collect(Collectors.toList());
    }

    private boolean shouldRelease(Player p, Philosophy phi, Club club) {
        if (p.isCaptain()) return false;
        if (p.getRole() == Player.PlayerRole.RETIRED) return false;

        return switch (phi) {
            case GALACTICO_ATTACK ->
                // OVR < 68 かつ loyalty < 60 なら放出
                p.getOverall() < 68 && p.getEmotion().getLoyalty() < 60;

            case YOUTH_ACADEMY ->
                // 高齢(35+)かつ潜在能力が低い
                p.getAge() >= 35 && p.getPotential() < 70;

            case PHYSICAL_PRESS ->
                // スタミナ < 55 の選手は不要
                p.getStamina() < 55 && p.getOverall() < 65;

            case MONEYBALL -> {
                // 給与 / OVR が割高（コスパ悪）
                double costPerOvr = p.getSalary() / (double) p.getOverall();
                yield costPerOvr > 18_000 && p.getOverall() < 72;
            }

            case PURE_DATA -> {
                // 感情パラメータが悪化している統計的非効率選手
                EmotionState e = p.getEmotion();
                yield e.getFrustration() >= 80
                    && e.getLoyalty() < 40
                    && p.getOverall() < 70;
            }

            case DEFENSIVE_INTELLECT, BALANCED ->
                // 明確な問題選手のみ放出
                p.getAge() >= 38 && p.getOverall() < 60;
        };
    }

    /** 補強対象選手を決定 */
    private List<Player> decideSignings(
            Club club, Philosophy phi,
            List<Player> freeAgents, List<Club> allClubs) {

        if (freeAgents == null || freeAgents.isEmpty()) {
            // フリーエージェントがいなければスカウトで生成
            freeAgents = scoutService.scout(club.getBudget());
        }

        int maxSignings = switch (phi) {
            case GALACTICO_ATTACK -> 3;
            case YOUTH_ACADEMY    -> 2;
            default               -> 1;
        };

        // ポジションの穴を特定
        Set<Player.Position> needed = findNeededPositions(club);

        return freeAgents.stream()
            .filter(p -> needed.contains(p.getPosition()))
            .filter(p -> matchesPhilosophyCriteria(p, phi))
            .sorted(signingComparator(phi))
            .limit(maxSignings)
            .collect(Collectors.toList());
    }

    /** 思想に合った補強基準 */
    private boolean matchesPhilosophyCriteria(Player p, Philosophy phi) {
        return switch (phi) {
            case GALACTICO_ATTACK  -> p.getOverall() >= 78;
            case YOUTH_ACADEMY     -> p.getAge() <= 21 && p.getPotential() >= 75;
            case PHYSICAL_PRESS    -> p.getStamina() >= 75 && p.getSpeed() >= 70;
            case DEFENSIVE_INTELLECT -> p.getDefending() >= 72;
            case MONEYBALL         -> {
                // OVR/給与 比が良い選手
                double costEff = p.getOverall() / (double)(p.getSalary() / 10_000.0);
                yield costEff >= 4.0 && p.getOverall() >= 65;
            }
            case PURE_DATA         -> p.getOverall() >= 72;
            case BALANCED          -> p.getOverall() >= 65;
        };
    }

    /** 思想に合ったソート順 */
    private Comparator<Player> signingComparator(Philosophy phi) {
        return switch (phi) {
            case GALACTICO_ATTACK  -> Comparator.comparingInt(Player::getOverall).reversed();
            case YOUTH_ACADEMY     -> Comparator.comparingInt(Player::getPotential).reversed();
            case PHYSICAL_PRESS    -> Comparator.comparingInt(Player::getStamina).reversed();
            case DEFENSIVE_INTELLECT -> Comparator.comparingInt(Player::getDefending).reversed();
            case MONEYBALL, PURE_DATA ->
                Comparator.comparingDouble((Player p) ->
                    (double) p.getOverall() / p.getSalary()).reversed();
            default ->
                Comparator.comparingInt(Player::getOverall).reversed();
        };
    }

    /** 補強が必要なポジションを特定 */
    private Set<Player.Position> findNeededPositions(Club club) {
        Set<Player.Position> needed = new HashSet<>();
        Map<Player.Position, Long> count = club.getRegistered().stream()
            .collect(Collectors.groupingBy(Player::getPosition, Collectors.counting()));

        if (count.getOrDefault(Player.Position.GK, 0L) < 1) needed.add(Player.Position.GK);
        if (count.getOrDefault(Player.Position.DF, 0L) < 3) needed.add(Player.Position.DF);
        if (count.getOrDefault(Player.Position.MF, 0L) < 3) needed.add(Player.Position.MF);
        if (count.getOrDefault(Player.Position.FW, 0L) < 2) needed.add(Player.Position.FW);

        // 穴がなければOVR最低ポジションを補強候補に
        if (needed.isEmpty()) {
            club.getRegistered().stream()
                .min(Comparator.comparingInt(Player::getOverall))
                .ifPresent(p -> needed.add(p.getPosition()));
        }
        return needed;
    }

    /** 移籍金の見積もり（思想によって相場感が異なる） */
    private long estimateTransferFee(Player p, Philosophy phi) {
        long base = (long)(p.getOverall() * p.getOverall() * 10_000L);
        return switch (phi) {
            case GALACTICO_ATTACK -> (long)(base * 1.4); // 高額でも買う
            case MONEYBALL        -> (long)(base * 0.7); // 安く買い叩く
            case YOUTH_ACADEMY    -> (long)(base * 0.8); // 若手は安め
            default               -> base;
        };
    }

    /** スカッドが手薄な場合に自動補充 */
    private void autoFillSquad(Club club, Philosophy phi) {
        int deficit = 11 - club.getRegistered().size();
        if (deficit <= 0) return;
        List<Player> newPlayers = scoutService.scout(club.getBudget());
        for (int i = 0; i < Math.min(deficit, newPlayers.size()); i++) {
            Player p = newPlayers.get(i);
            club.getSquad().add(p);
            p.setRole(Player.PlayerRole.REGISTERED);
        }
    }

    // ════════════════════════════════════════════════════════════
    // ユーティリティ
    // ════════════════════════════════════════════════════════════

    public Philosophy getPhilosophy(Club club) {
        return CLUB_PHILOSOPHY.getOrDefault(club.getName(), Philosophy.BALANCED);
    }

    public String getPhilosophyLabel(Club club) {
        return getPhilosophy(club).label;
    }

    // ════════════════════════════════════════════════════════════
    // 結果クラス
    // ════════════════════════════════════════════════════════════

    /** 週次AIの判断結果 */
    public static class AIWeeklyDecision {
        public final String clubName;
        public final int    week;
        public final List<String>                     logs         = new ArrayList<>();
        public final List<Map.Entry<Player, String>>  releaseFlags = new ArrayList<>();

        public AIWeeklyDecision(String clubName, int week) {
            this.clubName = clubName;
            this.week     = week;
        }

        public void log(String msg) { logs.add(msg); }

        public void flagForRelease(Player p, String reason) {
            releaseFlags.add(Map.entry(p, reason));
        }

        public boolean hasActions() {
            return !logs.isEmpty() || !releaseFlags.isEmpty();
        }

        @Override public String toString() {
            return String.format("[ClubAI] %s 第%d週: %s",
                clubName, week, String.join(" / ", logs));
        }
    }

    /** 移籍ウィンドウAIの判断結果 */
    public static class AITransferDecision {
        public final String       clubName;
        public final List<Player> releases = new ArrayList<>();
        public final List<Signing> signings = new ArrayList<>();

        public AITransferDecision(String clubName) { this.clubName = clubName; }

        public record Signing(Player player, long fee) {}

        public boolean hasActivity() {
            return !releases.isEmpty() || !signings.isEmpty();
        }

        @Override public String toString() {
            return String.format("[ClubAI Transfer] %s: 放出%d名 獲得%d名",
                clubName, releases.size(), signings.size());
        }
    }
}
