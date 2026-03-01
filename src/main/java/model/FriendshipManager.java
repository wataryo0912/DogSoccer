package model;

import db.FriendshipDao;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 選手間仲良し度の全体管理クラス
 *
 * 【責務】
 *  - Friendship オブジェクトをメモリ上で保持（Map<"idA_idB", Friendship>）
 *  - ポイント加算・レベルアップ処理
 *  - 試合出場メンバーへのボーナス一時適用 / 解除
 *  - 犬種・ポジション類似ボーナスの計算
 *  - FriendshipDao を通じた DB 永続化
 *
 * 【呼び出しタイミング】
 *  - 試合後    : recordMatch(squad)
 *  - 練習後    : recordTraining(squad)
 *  - イベント後: recordEvent(targetPlayer, allSquad)
 *  - 試合開始前: applyMatchBonuses(lineup)
 *  - 試合終了後: removeMatchBonuses(lineup)
 */
public class FriendshipManager {

    // ── 内部データ ──────────────────────────────────────────
    /** key = "minId_maxId" */
    private final Map<String, Friendship> friendships = new LinkedHashMap<>();
    private final FriendshipDao           dao         = new FriendshipDao();

    // ── 初期化 ──────────────────────────────────────────────

    /** DBからロード */
    public void loadFromDB() {
        friendships.clear();
        for (Friendship f : dao.findAll()) {
            friendships.put(f.key(), f);
        }
        System.out.println("[Friendship] ロード: " + friendships.size() + "ペア");
    }

    /** 全データをDBに保存 */
    public void saveAll() {
        for (Friendship f : friendships.values()) {
            dao.upsert(f);
        }
    }

    // ── ポイント加算 ──────────────────────────────────────

    /**
     * ペアを取得 or 新規作成し、ポイントを加算する。
     * 犬種・ポジション一致の追加ボーナスも計算する。
     *
     * @return レベルアップしたペアのメッセージリスト
     */
    public List<String> addPoints(Player a, Player b, int basePoints) {
        List<String> messages = new ArrayList<>();
        if (a == null || b == null || a.getId() == b.getId()) return messages;
        if (a.getId() <= 0 || b.getId() <= 0) return messages;

        int delta = basePoints;

        // 同じ犬種ボーナス
        if (a.getBreed() != null && a.getBreed().equals(b.getBreed())) {
            delta += Friendship.PTS_SAME_BREED;
        }
        // 同じポジションボーナス
        if (a.getPosition() == b.getPosition()) {
            delta += Friendship.PTS_SAME_POS;
        }

        String key = Friendship.makeKey(a.getId(), b.getId());
        Friendship f = friendships.computeIfAbsent(key,
            k -> new Friendship(a.getId(), b.getId()));

        boolean levelUp = f.addPoints(delta);
        dao.upsert(f);  // 即時保存

        if (levelUp) {
            messages.add(String.format("🐾 %s ↔ %s の仲良し度が %s になった！",
                a.getFullName(), b.getFullName(), f.getLevel().label));
        }
        return messages;
    }

    /**
     * 試合後のポイント加算（全出場選手の全ペアに付与）
     */
    public List<String> recordMatch(List<Player> squad) {
        return recordGroup(squad, Friendship.PTS_MATCH);
    }

    /**
     * 練習後のポイント加算
     */
    public List<String> recordTraining(List<Player> squad) {
        return recordGroup(squad, Friendship.PTS_TRAINING);
    }

    /**
     * 週次イベント後のポイント加算
     * targetPlayer が null の場合は全体イベント（全ペアに少量付与）
     */
    public List<String> recordEvent(Player targetPlayer, List<Player> squad) {
        List<String> messages = new ArrayList<>();
        if (squad == null || squad.isEmpty()) return messages;

        if (targetPlayer == null) {
            // 全体イベント: ランダムな隣接ペア数組にだけ少量付与（重すぎないように）
            List<Player> shuffled = new ArrayList<>(squad);
            Collections.shuffle(shuffled);
            int pairs = Math.min(3, shuffled.size() / 2);
            for (int i = 0; i < pairs; i++) {
                messages.addAll(addPoints(shuffled.get(i * 2), shuffled.get(i * 2 + 1),
                    Friendship.PTS_EVENT_TARGET / 2));
            }
        } else {
            // 特定選手イベント: その選手と全員にポイント付与
            for (Player other : squad) {
                if (!other.equals(targetPlayer)) {
                    messages.addAll(addPoints(targetPlayer, other,
                        Friendship.PTS_EVENT_TARGET));
                }
            }
        }
        return messages;
    }

    /** n人のグループ全ペアにポイントを付与 */
    private List<String> recordGroup(List<Player> squad, int pts) {
        List<String> messages = new ArrayList<>();
        for (int i = 0; i < squad.size(); i++) {
            for (int j = i + 1; j < squad.size(); j++) {
                messages.addAll(addPoints(squad.get(i), squad.get(j), pts));
            }
        }
        return messages;
    }

    // ── 試合中ボーナス ──────────────────────────────────────

    /**
     * 出場メンバー同士の仲良し度ボーナスを一時的に能力値に加算する。
     * 試合終了後に必ず removeMatchBonuses() を呼ぶこと。
     *
     * @return 適用されたボーナスの説明文リスト
     */
    public List<String> applyMatchBonuses(List<Player> lineup) {
        List<String> log = new ArrayList<>();
        if (lineup == null) return log;

        for (int i = 0; i < lineup.size(); i++) {
            for (int j = i + 1; j < lineup.size(); j++) {
                Player a = lineup.get(i);
                Player b = lineup.get(j);
                Friendship f = get(a, b);
                if (f == null || f.getLevel() == Friendship.Level.NONE) continue;

                Map<String, Integer> bonus = f.getMatchBonus();
                applyBonus(a, bonus);
                applyBonus(b, bonus);

                if (!bonus.isEmpty()) {
                    log.add(String.format("💛 %s & %s [%s] → 能力UP",
                        a.getFullName(), b.getFullName(), f.getLevel().label));
                }
            }
        }
        return log;
    }

    /**
     * 試合中に加算したボーナスを取り除く（マイナスして元に戻す）。
     */
    public void removeMatchBonuses(List<Player> lineup) {
        if (lineup == null) return;
        for (int i = 0; i < lineup.size(); i++) {
            for (int j = i + 1; j < lineup.size(); j++) {
                Player a = lineup.get(i);
                Player b = lineup.get(j);
                Friendship f = get(a, b);
                if (f == null || f.getLevel() == Friendship.Level.NONE) continue;

                Map<String, Integer> bonus = f.getMatchBonus();
                // マイナス適用で元に戻す
                Map<String, Integer> remove = new LinkedHashMap<>();
                bonus.forEach((k, v) -> remove.put(k, -v));
                applyBonus(a, remove);
                applyBonus(b, remove);
            }
        }
    }

    private void applyBonus(Player p, Map<String, Integer> bonus) {
        bonus.forEach((stat, val) -> {
            switch (stat) {
                case "speed"     -> p.setSpeed    (p.getSpeed()     + val);
                case "shooting"  -> p.setShooting (p.getShooting()  + val);
                case "passing"   -> p.setPassing  (p.getPassing()   + val);
                case "defending" -> p.setDefending(p.getDefending() + val);
                case "stamina"   -> p.setStamina  (p.getStamina()   + val);
                case "spirit"    -> p.setSpirit   (p.getSpirit()    + val);
            }
        });
    }

    // ── 照会 ──────────────────────────────────────────────

    /** 2選手間のFriendshipを取得（なければnull） */
    public Friendship get(Player a, Player b) {
        if (a == null || b == null) return null;
        return friendships.get(Friendship.makeKey(a.getId(), b.getId()));
    }

    /** 指定選手の全仲良しペアを仲良し度の高い順に返す */
    public List<Friendship> getFriendshipsOf(Player player) {
        return friendships.values().stream()
            .filter(f -> f.involves(player.getId()))
            .filter(f -> f.getLevel() != Friendship.Level.NONE)
            .sorted(Comparator.comparingInt(f -> -f.getPoints()))
            .collect(Collectors.toList());
    }

    /** 全仲良しペアをポイント降順で返す */
    public List<Friendship> getAllSorted() {
        return friendships.values().stream()
            .filter(f -> f.getLevel() != Friendship.Level.NONE)
            .sorted(Comparator.comparingInt(f -> -f.getPoints()))
            .collect(Collectors.toList());
    }

    /** チーム全体の平均仲良し度（SMALL=1, MEDIUM=2, LARGE=3の平均） */
    public double getTeamChemistry(List<Player> squad) {
        if (squad == null || squad.size() < 2) return 0.0;
        int total = 0, count = 0;
        for (int i = 0; i < squad.size(); i++) {
            for (int j = i + 1; j < squad.size(); j++) {
                Friendship f = get(squad.get(i), squad.get(j));
                total += (f != null ? f.getLevel().ordinal() : 0);
                count++;
            }
        }
        return count > 0 ? (double) total / count : 0.0;
    }

    /** 全ペア数 */
    public int size() { return friendships.size(); }
}
