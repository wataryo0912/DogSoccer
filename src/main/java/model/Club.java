package model;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * クラブクラス
 *
 * 【画像・データ紐づけ設計】
 *   Excelの「ユニフォーム」列 → uniform_image_file → DB clubs.uniform_image
 *   実ファイルパス: resources/<uniform_image_file>
 */
public class Club {

    private int    id;
    private String name;
    private String color             = "#ff6b00";
    private String formation         = "4-4-2";
    private String breed             = "柴犬";
    private String uniformImageFile  = "";   // チームユニフォーム画像（Excelから）
    private long   budget;
    private long   weeklySalaryBudget;
    private int    teamHarmony       = 50;
    private List<Player> squad = new ArrayList<>();
    private int wins, draws, losses;

    public Club(String name, long budget, long weeklySalaryBudget) {
        this.name               = name;
        this.budget             = budget;
        this.weeklySalaryBudget = weeklySalaryBudget;
    }

    // ── ビジネスロジック ─────────────────────────────────────
    public long getTotalWeeklySalary() {
        return squad.stream().mapToLong(Player::getSalary).sum();
    }
    public double getAverageOverall() {
        return squad.isEmpty() ? 0 : squad.stream().mapToInt(Player::getOverall).average().orElse(0);
    }
    public boolean signPlayer(Player p, long transferFee) {
        if (budget < transferFee) return false;
        if (getTotalWeeklySalary() + p.getSalary() > weeklySalaryBudget) return false;
        budget -= transferFee;
        squad.add(p);
        return true;
    }
    public boolean sellPlayer(Player p, long fee) {
        if (!squad.contains(p)) return false;
        squad.remove(p);
        budget += fee;
        return true;
    }
    public void recordResult(int gf, int ga) {
        if      (gf > ga)  wins++;
        else if (gf == ga) draws++;
        else               losses++;
    }
    public int getPoints() { return wins * 3 + draws; }

    /** シーズン開始時に成績をリセット */
    public void resetRecord() { wins = 0; draws = 0; losses = 0; }

    // ── キャプテン管理（1クラブ1人制約） ────────────────────
    /**
     * 指定選手をキャプテンに設定する。
     * 既存のキャプテンは自動的に解除される（1クラブ1人制約）。
     */
    public void setCaptain(Player newCaptain) {
        squad.forEach(p -> p.setCaptain(false));   // 全員解除
        if (newCaptain != null) newCaptain.setCaptain(true);
    }

    /** 現在のキャプテンを返す（いなければ null） */
    public Player getCaptain() {
        return squad.stream().filter(Player::isCaptain).findFirst().orElse(null);
    }

    // ── 役割別フィルター ────────────────────────────────────
    /** 試合登録メンバー（REGISTERED）のみ */
    public List<Player> getRegistered() {
        return squad.stream()
            .filter(p -> p.getRole() == Player.PlayerRole.REGISTERED)
            .collect(Collectors.toList());
    }

    /** ベンチ要員（BENCH）のみ */
    public List<Player> getBenchPlayers() {
        return squad.stream()
            .filter(p -> p.getRole() == Player.PlayerRole.BENCH)
            .collect(Collectors.toList());
    }

    /** スカッド外（INACTIVE）のみ */
    public List<Player> getInactivePlayers() {
        return squad.stream()
            .filter(p -> p.getRole() == Player.PlayerRole.INACTIVE)
            .collect(Collectors.toList());
    }

    /** 下部組織（ACADEMY）のみ */
    public List<Player> getAcademyPlayers() {
        return squad.stream()
            .filter(p -> p.getRole() == Player.PlayerRole.ACADEMY)
            .collect(Collectors.toList());
    }

    /** EmotionEngine互換: squadをplayersとして返す */
    public List<Player> getPlayers() {
        return squad;
    }

    /** 昇格申請中の下部組織選手 */
    public List<Player> getPendingPromotions() {
        return squad.stream()
            .filter(Player::isPendingPromotion)
            .collect(Collectors.toList());
    }

    /**
     * 下部組織選手の昇格申請を受け付ける。
     * 実際の昇格（ACADEMY → REGISTERED）は翌週 processPromotions() で行う。
     */
    public boolean requestPromotion(Player player) {
        if (player.getRole() != Player.PlayerRole.ACADEMY) return false;
        player.setPendingPromotion(true);
        return true;
    }

    /**
     * 保留中の昇格を一括処理する（週が進んだタイミングで呼ぶ）。
     * @return 昇格した選手リスト
     */
    public List<Player> processPromotions() {
        List<Player> promoted = new ArrayList<>();
        for (Player p : squad) {
            if (p.isPendingPromotion()) {
                p.setRole(Player.PlayerRole.REGISTERED);
                p.setPendingPromotion(false);
                promoted.add(p);
            }
        }
        return promoted;
    }

    // ── Getters ──────────────────────────────────────────────
    public int          getId()                 { return id; }
    public String       getName()               { return name; }
    public String       getColor()              { return color; }
    public String       getFormation()          { return formation; }
    public String       getBreed()              { return breed; }
    public String       getUniformImageFile()   { return uniformImageFile; }
    public long         getBudget()             { return budget; }
    public long         getWeeklySalaryBudget() { return weeklySalaryBudget; }
    public int          getTeamHarmony()        { return teamHarmony; }
    public List<Player> getSquad()              { return squad; }
    public int          getWins()               { return wins; }
    public int          getDraws()              { return draws; }
    public int          getLosses()             { return losses; }

    // ── Setters ──────────────────────────────────────────────
    public void setId(int id)                   { this.id = id; }
    public void setColor(String v)              { this.color = v; }
    public void setFormation(String v)          { this.formation = v; }
    public void setBreed(String v)              { this.breed = v; }
    public void setUniformImageFile(String v)   { this.uniformImageFile = v != null ? v : ""; }
    public void setBudget(long v)               { this.budget = v; }
    public void setTeamHarmony(int v)           { this.teamHarmony = Math.max(0, Math.min(100, v)); }

    @Override public String toString() {
        return String.format("%s [%s] %s | 予算:¥%,d | %dW-%dD-%dL",
                name, breed, formation, budget, wins, draws, losses);
    }
}
